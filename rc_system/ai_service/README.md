AI Recommendation Service (FastAPI)

Setup
- Create a virtual environment and install dependencies from requirements.txt.
- Configure database connection via environment variables.

Environment variables
- DB_SERVER (default: localhost)
- DB_PORT (default: 1433)
- DB_NAME (default: GoodHealthSystem)
- DB_USER (default: sa)
- DB_PASSWORD (default: 123)
- DB_DRIVER (default: ODBC Driver 17 for SQL Server)
- DB_ENCRYPT (default: yes)
- DB_TRUST_SERVER_CERT (default: yes)

Run
- uvicorn main:app --host 0.0.0.0 --port 8001

Endpoints
- GET /api/ai/health
- GET /api/ai/recommend/user?userId=1&topK=5
- GET /api/ai/recommend/item?userId=1&topK=5
- GET /api/ai/recommend/hybrid?userId=1&topK=5&userWeight=0.6
- GET /api/ai/recommend/top-rated?userId=1&topK=5
