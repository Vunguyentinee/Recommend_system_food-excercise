import logging
import os
import urllib.parse
from typing import Dict, List, Tuple

from fastapi import FastAPI, Query
from sqlalchemy import create_engine, text
from sqlalchemy.engine import Engine

app = FastAPI(title="AI Recommendation Service", version="1.0.0")
logger = logging.getLogger("ai_service")
logging.basicConfig(level=logging.INFO)


def _get_env(name: str, default: str = "") -> str:
    value = os.getenv(name)
    return value if value is not None else default


def build_db_url() -> str:
    driver = _get_env("DB_DRIVER", "ODBC Driver 17 for SQL Server")
    server = _get_env("DB_SERVER", "localhost")
    port = _get_env("DB_PORT", "1433")
    database = _get_env("DB_NAME", "GoodHealthSystem")
    user = _get_env("DB_USER", "sa")
    password = _get_env("DB_PASSWORD", "123")
    encrypt = _get_env("DB_ENCRYPT", "yes")
    trust_cert = _get_env("DB_TRUST_SERVER_CERT", "yes")

    odbc_params = (
        f"DRIVER={driver};"
        f"SERVER={server},{port};"
        f"DATABASE={database};"
        f"UID={user};"
        f"PWD={password};"
        f"Encrypt={encrypt};"
        f"TrustServerCertificate={trust_cert};"
    )
    return "mssql+pyodbc:///?odbc_connect=" + urllib.parse.quote_plus(odbc_params)


def create_db_engine() -> Engine:
    url = build_db_url()
    return create_engine(url, pool_pre_ping=True)


def fetch_interactions(engine: Engine) -> List[Tuple[int, int, float]]:
    sql = text(
        "SELECT UserID, FoodID, Rating "
        "FROM Interaction_Logs "
        "WHERE FoodID IS NOT NULL"
    )
    with engine.connect() as conn:
        rows = conn.execute(sql).fetchall()
    return [(int(r[0]), int(r[1]), float(r[2])) for r in rows if r[0] is not None and r[1] is not None]


def build_user_item_matrix(rows: List[Tuple[int, int, float]]) -> Dict[int, Dict[int, float]]:
    matrix: Dict[int, Dict[int, float]] = {}
    for user_id, food_id, rating in rows:
        matrix.setdefault(user_id, {})[food_id] = rating
    return matrix


def calculate_similarity(user1: Dict[int, float], user2: Dict[int, float]) -> float:
    common = set(user1.keys()) & set(user2.keys())
    if not common:
        return 0.0

    dot = sum(user1[fid] * user2[fid] for fid in common)
    norm1 = sum(r * r for r in user1.values())
    norm2 = sum(r * r for r in user2.values())
    if norm1 == 0 or norm2 == 0:
        return 0.0
    return dot / ((norm1 ** 0.5) * (norm2 ** 0.5))


def recommend_user_based(matrix: Dict[int, Dict[int, float]], user_id: int, top_k: int) -> List[int]:
    target = matrix.get(user_id, {})
    similarities: Dict[int, float] = {}

    for other_id, ratings in matrix.items():
        if other_id == user_id:
            continue
        sim = calculate_similarity(target, ratings)
        if sim > 0:
            similarities[other_id] = sim

    predicted: Dict[int, float] = {}
    weights: Dict[int, float] = {}

    for other_id, sim in similarities.items():
        for food_id, rating in matrix[other_id].items():
            if food_id not in target:
                predicted[food_id] = predicted.get(food_id, 0.0) + rating * sim
                weights[food_id] = weights.get(food_id, 0.0) + sim

    scores = []
    for food_id, score in predicted.items():
        weight = weights.get(food_id, 0.0)
        if weight > 0:
            scores.append((food_id, score / weight))

    scores.sort(key=lambda x: x[1], reverse=True)
    return [fid for fid, _ in scores[:top_k]]


def recommend_item_based(matrix: Dict[int, Dict[int, float]], user_id: int, top_k: int) -> List[int]:
    user_ratings = matrix.get(user_id, {})
    if not user_ratings:
        return []

    all_food_ids = set()
    for ratings in matrix.values():
        all_food_ids.update(ratings.keys())

    scores: Dict[int, float] = {}

    for rated_food_id, rating_score in user_ratings.items():
        if rating_score < 3.0:
            continue

        for food_id in all_food_ids:
            if food_id == rated_food_id or food_id in user_ratings:
                continue

            food1_users = {}
            food2_users = {}
            for user, ratings in matrix.items():
                if rated_food_id in ratings:
                    food1_users[user] = ratings[rated_food_id]
                if food_id in ratings:
                    food2_users[user] = ratings[food_id]

            similarity = calculate_similarity(food1_users, food2_users)
            scores[food_id] = scores.get(food_id, 0.0) + similarity * rating_score

    return [fid for fid, _ in sorted(scores.items(), key=lambda x: x[1], reverse=True)[:top_k]]


def recommend_hybrid(matrix: Dict[int, Dict[int, float]], user_id: int, top_k: int, user_weight: float) -> List[int]:
    user_weight = min(max(user_weight, 0.0), 1.0)
    user_based = recommend_user_based(matrix, user_id, top_k * 2)
    item_based = recommend_item_based(matrix, user_id, top_k * 2)

    combined: Dict[int, float] = {}

    for i, fid in enumerate(user_based):
        score = (top_k - i) * user_weight
        combined[fid] = combined.get(fid, 0.0) + score

    for i, fid in enumerate(item_based):
        score = (top_k - i) * (1.0 - user_weight)
        combined[fid] = combined.get(fid, 0.0) + score

    return [fid for fid, _ in sorted(combined.items(), key=lambda x: x[1], reverse=True)[:top_k]]


def top_rated_by_user(matrix: Dict[int, Dict[int, float]], user_id: int, top_k: int) -> List[int]:
    target = matrix.get(user_id, {})
    if not target:
        return []

    similarities: Dict[int, float] = {}
    for other_id, ratings in matrix.items():
        if other_id == user_id:
            continue
        sim = calculate_similarity(target, ratings)
        if sim > 0:
            similarities[other_id] = sim

    scores: Dict[int, float] = {}
    for other_id, sim in similarities.items():
        for food_id, rating in matrix[other_id].items():
            if food_id not in target:
                scores[food_id] = scores.get(food_id, 0.0) + rating * sim

    return [fid for fid, _ in sorted(scores.items(), key=lambda x: x[1], reverse=True)[:top_k]]


def build_matrix() -> Dict[int, Dict[int, float]]:
    engine = create_db_engine()
    rows = fetch_interactions(engine)
    return build_user_item_matrix(rows)


@app.get("/api/ai/health")
def health() -> Dict[str, str]:
    return {"status": "ok"}


@app.get("/api/ai/recommend/user")
def recommend_user(userId: int = Query(...), topK: int = Query(5)) -> Dict[str, List[int]]:
    matrix = build_matrix()
    return {"data": recommend_user_based(matrix, userId, topK)}


@app.get("/api/ai/recommend/item")
def recommend_item(userId: int = Query(...), topK: int = Query(5)) -> Dict[str, List[int]]:
    matrix = build_matrix()
    return {"data": recommend_item_based(matrix, userId, topK)}


@app.get("/api/ai/recommend/hybrid")
def recommend_hybrid_cf(
    userId: int = Query(...),
    topK: int = Query(5),
    userWeight: float = Query(0.6),
) -> Dict[str, List[int]]:
    matrix = build_matrix()
    return {"data": recommend_hybrid(matrix, userId, topK, userWeight)}


@app.get("/api/ai/recommend/top-rated")
def recommend_top_rated(userId: int = Query(...), topK: int = Query(5)) -> Dict[str, List[int]]:
    matrix = build_matrix()
    return {"data": top_rated_by_user(matrix, userId, topK)}
