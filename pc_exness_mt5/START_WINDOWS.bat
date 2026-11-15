@echo off
cd /d "%~dp0"
title CryptoQuant PC - Exness & MT5 AI Bot
echo Memeriksa Python & MetaTrader5...
python -c "import MetaTrader5" 2>nul || python -m pip install -r requirements.txt
echo Menjalankan CryptoQuant PC Desktop App...
python exness_mt5_ai_bot.py
pause
