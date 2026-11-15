import csv
import json
import math
import os
import socket
import sqlite3
import threading
import time
import tkinter as tk
from datetime import datetime, timedelta
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from tkinter import filedialog, messagebox, ttk
from typing import Any, Dict, List, Optional, Tuple
import urllib.error
import urllib.request

try:
    import MetaTrader5 as mt5
    MT5_AVAILABLE = True
except ImportError:
    mt5 = None
    MT5_AVAILABLE = False

DB_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "ai_trade_history.db")

EXNESS_DEMO_SERVERS = [
    "Exness-MT5Trial",
    "Exness-MT5Trial2",
    "Exness-MT5Trial5",
    "Exness-MT5Trial6",
    "Exness-MT5Trial7",
]

EXNESS_REAL_SERVERS = [
    "Exness-MT5Real",
    "Exness-MT5Real2",
    "Exness-MT5Real5",
    "Exness-MT5Real6",
    "Exness-MT5Real7",
    "Exness-MT5Real9",
    "Exness-MT5Real11",
]

EXNESS_MARKETS = [
    "BTCUSDm",
    "ETHUSDm",
    "SOLUSDm",
    "BNBUSDm",
    "XRPUSDm",
    "XAUUSDm",
    "XAGUSDm",
    "EURUSDm",
    "GBPUSDm",
    "USDJPYm",
    "AUDUSDm",
    "USOILm",
    "USTECm",
    "US30m",
]


def get_local_lan_ip() -> str:
    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.connect(("8.8.8.8", 80))
        ip = sock.getsockname()[0]
        sock.close()
        return ip
    except Exception:
        return "127.0.0.1"


class TradeHistoryStore:
    """Lightweight SQLite store for AI-executed trades and reasoning."""

    def __init__(self, db_path: str = DB_FILE) -> None:
        self.db_path = db_path
        self._lock = threading.Lock()
        self._init_db()

    def _init_db(self) -> None:
        with self._lock, sqlite3.connect(self.db_path) as conn:
            conn.execute(
                """
                CREATE TABLE IF NOT EXISTS ai_trades (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    ticket INTEGER,
                    timestamp TEXT NOT NULL,
                    broker_server TEXT,
                    symbol TEXT NOT NULL,
                    strategy TEXT NOT NULL,
                    action TEXT NOT NULL,
                    volume REAL NOT NULL,
                    entry_price REAL NOT NULL,
                    exit_price REAL,
                    sl REAL,
                    tp REAL,
                    pnl REAL DEFAULT 0.0,
                    status TEXT NOT NULL,
                    ai_reason TEXT NOT NULL
                )
                """
            )
            conn.commit()

    def record_trade(
        self,
        ticket: int,
        broker_server: str,
        symbol: str,
        strategy: str,
        action: str,
        volume: float,
        entry_price: float,
        sl: float,
        tp: float,
        ai_reason: str,
        status: str = "OPEN",
        pnl: float = 0.0,
        exit_price: Optional[float] = None,
    ) -> None:
        ts = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        with self._lock, sqlite3.connect(self.db_path) as conn:
            conn.execute(
                """
                INSERT INTO ai_trades (
                    ticket, timestamp, broker_server, symbol, strategy, action,
                    volume, entry_price, exit_price, sl, tp, pnl, status, ai_reason
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    ticket,
                    ts,
                    broker_server,
                    symbol,
                    strategy,
                    action,
                    volume,
                    entry_price,
                    exit_price,
                    sl,
                    tp,
                    pnl,
                    status,
                    ai_reason,
                ),
            )
            conn.commit()

    def sync_closed_deals(self, deals: List[Tuple[int, float, float]]) -> None:
        """Update PnL and exit price from MT5 closed deals: (position_id, exit_price, profit)."""
        if not deals:
            return
        with self._lock, sqlite3.connect(self.db_path) as conn:
            for pos_id, exit_px, profit in deals:
                conn.execute(
                    """
                    UPDATE ai_trades
                    SET exit_price = ?, pnl = ?, status = 'CLOSED'
                    WHERE ticket = ? AND status != 'CLOSED'
                    """,
                    (exit_px, profit, pos_id),
                )
            conn.commit()

    def fetch_all(self, strategy_filter: str = "ALL") -> List[tuple]:
        with self._lock, sqlite3.connect(self.db_path) as conn:
            if strategy_filter != "ALL":
                rows = conn.execute(
                    "SELECT id, ticket, timestamp, broker_server, symbol, strategy, action, volume, entry_price, exit_price, pnl, status, ai_reason "
                    "FROM ai_trades WHERE strategy = ? ORDER BY id DESC",
                    (strategy_filter,),
                ).fetchall()
            else:
                rows = conn.execute(
                    "SELECT id, ticket, timestamp, broker_server, symbol, strategy, action, volume, entry_price, exit_price, pnl, status, ai_reason "
                    "FROM ai_trades ORDER BY id DESC"
                ).fetchall()
        return rows


def compute_indicators(closes: List[float], highs: List[float], lows: List[float]) -> Dict[str, float]:
    """Fast, zero-dependency technical indicator engine (RSI, EMA20, EMA50, Bollinger Bands, ATR)."""
    if len(closes) < 20:
        last = closes[-1] if closes else 0.0
        return {
            "price": last,
            "rsi": 50.0,
            "ema20": last,
            "ema50": last,
            "bb_upper": last * 1.01,
            "bb_mid": last,
            "bb_lower": last * 0.99,
            "atr": last * 0.005,
        }

    gains, losses = 0.0, 0.0
    for i in range(1, 15):
        diff = closes[i] - closes[i - 1]
        if diff >= 0:
            gains += diff
        else:
            losses += -diff
    avg_gain, avg_loss = gains / 14.0, losses / 14.0
    for i in range(15, len(closes)):
        diff = closes[i] - closes[i - 1]
        g = diff if diff >= 0 else 0.0
        l = -diff if diff < 0 else 0.0
        avg_gain = (avg_gain * 13.0 + g) / 14.0
        avg_loss = (avg_loss * 13.0 + l) / 14.0
    rsi = 100.0 if avg_loss == 0 else 100.0 - (100.0 / (1.0 + (avg_gain / avg_loss)))

    def ema(values: List[float], period: int) -> float:
        k = 2.0 / (period + 1)
        val = values[0]
        for v in values[1:]:
            val = v * k + val * (1.0 - k)
        return val

    ema20 = ema(closes[-40:], 20)
    ema50 = ema(closes[-60:], min(50, len(closes)))

    window = closes[-20:]
    sma = sum(window) / len(window)
    variance = sum((x - sma) ** 2 for x in window) / len(window)
    std = math.sqrt(variance)
    bb_upper = sma + 2.0 * std
    bb_lower = sma - 2.0 * std

    trs = []
    for i in range(max(1, len(closes) - 14), len(closes)):
        tr = max(
            highs[i] - lows[i],
            abs(highs[i] - closes[i - 1]),
            abs(lows[i] - closes[i - 1]),
        )
        trs.append(tr)
    atr = sum(trs) / len(trs) if trs else 0.0

    return {
        "price": closes[-1],
        "rsi": rsi,
        "ema20": ema20,
        "ema50": ema50,
        "bb_upper": bb_upper,
        "bb_mid": sma,
        "bb_lower": bb_lower,
        "atr": atr,
    }


def query_gemini_confirmation(
    api_key: str,
    symbol: str,
    strategy: str,
    candidate_action: str,
    ind: Dict[str, float],
) -> Tuple[bool, str]:
    """Optional lightweight REST call to Gemini API for AI trade confirmation."""
    if not api_key.strip():
        reason = (
            f"[{strategy}] RSI={ind['rsi']:.1f}, EMA20={ind['ema20']:.2f}, "
            f"BB=[{ind['bb_lower']:.2f}-{ind['bb_upper']:.2f}], ATR={ind['atr']:.4f}"
        )
        return True, reason

    prompt = (
        f"You are an Exness MT5 algorithmic trading validator. Symbol: {symbol}. "
        f"Strategy: {strategy}. Proposed action: {candidate_action}. "
        f"Price={ind['price']:.4f}, RSI14={ind['rsi']:.2f}, EMA20={ind['ema20']:.4f}, "
        f"EMA50={ind['ema50']:.4f}, BB_Upper={ind['bb_upper']:.4f}, BB_Lower={ind['bb_lower']:.4f}, "
        f"ATR14={ind['atr']:.4f}. Reply strictly in JSON: "
        '{"approve": true/false, "reason": "short Indonesian explanation max 18 words"}'
    )

    url = (
        "https://generativelanguage.googleapis.com/v1beta/models/"
        f"gemini-2.5-flash:generateContent?key={api_key.strip()}"
    )
    body = json.dumps({"contents": [{"parts": [{"text": prompt}]}]}).encode("utf-8")
    req = urllib.request.Request(
        url,
        data=body,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            text = data["candidates"][0]["content"]["parts"][0]["text"]
            text = text.replace("```json", "").replace("```", "").strip()
            parsed = json.loads(text)
            return bool(parsed.get("approve", True)), str(parsed.get("reason", "Disetujui AI"))
    except Exception as exc:
        return True, f"Fallback TA ({exc})"


class ExnessMt5PcApp:
    def __init__(self, root: tk.Tk) -> None:
        self.root = root
        self.root.title("CryptoQuant PC — Exness & MT5 AI Bridge")
        self.root.geometry("1040x740")
        self.root.configure(bg="#0B0E14")

        self.store = TradeHistoryStore()
        self.is_connected = False
        self.is_bot_running = False
        self.bot_thread: Optional[threading.Thread] = None
        self.last_trade_time = 0.0
        self._mt5_lock = threading.Lock()

        self.bridge_server: Optional[ThreadingHTTPServer] = None
        self.bridge_port = 8000
        self.bridge_token = "exness-ai-2026"

        self._setup_styles()
        self._build_ui()
        self.refresh_history_table()
        self.start_android_bridge()

    def _setup_styles(self) -> None:
        style = ttk.Style()
        style.theme_use("clam")
        style.configure("TNotebook", background="#0B0E14", borderwidth=0)
        style.configure("TNotebook.Tab", background="#141824", foreground="#94A3B8", padding=[14, 8])
        style.map("TNotebook.Tab", background=[("selected", "#1E2436")], foreground=[("selected", "#00E5FF")])
        style.configure("Treeview", background="#141824", foreground="#F1F5F9", fieldbackground="#141824", rowheight=26)
        style.configure("Treeview.Heading", background="#1E2436", foreground="#00E5FF", font=("Segoe UI", 9, "bold"))

    def _build_ui(self) -> None:
        header = tk.Frame(self.root, bg="#141824", pady=10, padx=16)
        header.pack(fill=tk.X)

        tk.Label(
            header,
            text="CRYPTOQUANT PC — EXNESS MT5 & ANDROID BRIDGE",
            bg="#141824",
            fg="#00E5FF",
            font=("Segoe UI", 13, "bold"),
        ).pack(side=tk.LEFT)

        self.status_lbl = tk.Label(
            header,
            text="● Belum Terhubung ke Exness MT5",
            bg="#141824",
            fg="#FF3B69",
            font=("Segoe UI", 10, "bold"),
        )
        self.status_lbl.pack(side=tk.RIGHT)

        notebook = ttk.Notebook(self.root)
        notebook.pack(fill=tk.BOTH, expand=True, padx=12, pady=10)

        self.tab_terminal = tk.Frame(notebook, bg="#0B0E14")
        self.tab_history = tk.Frame(notebook, bg="#0B0E14")
        notebook.add(self.tab_terminal, text="Login Exness & Bot AI")
        notebook.add(self.tab_history, text="Histori Trading AI")

        self._build_terminal_tab()
        self._build_history_tab()

    def _build_terminal_tab(self) -> None:
        top_frame = tk.Frame(self.tab_terminal, bg="#0B0E14")
        top_frame.pack(fill=tk.X, pady=6)

        # Left: Exness Login + Android Bridge Box
        conn_box = tk.LabelFrame(
            top_frame,
            text=" 1. Login Akun Exness (Demo / Riil) & Koneksi Android ",
            bg="#141824",
            fg="#00E5FF",
            font=("Segoe UI", 10, "bold"),
            padx=12,
            pady=10,
        )
        conn_box.pack(side=tk.LEFT, fill=tk.BOTH, expand=True, padx=(0, 8))

        self.var_acc_mode = tk.StringVar(value="DEMO")
        self.var_login = tk.StringVar()
        self.var_password = tk.StringVar()
        self.var_server = tk.StringVar(value="Exness-MT5Trial")
        self.var_mt5_path = tk.StringVar()
        self.var_bridge_token = tk.StringVar(value=self.bridge_token)

        mode_row = tk.Frame(conn_box, bg="#141824")
        mode_row.grid(row=0, column=0, columnspan=2, sticky="ew", pady=(0, 6))
        tk.Label(mode_row, text="Tipe Akun:", bg="#141824", fg="#94A3B8", font=("Segoe UI", 9)).pack(side=tk.LEFT, padx=(0, 10))
        tk.Radiobutton(
            mode_row,
            text="Demo (Exness-MT5Trial)",
            variable=self.var_acc_mode,
            value="DEMO",
            command=self._on_account_mode_changed,
            bg="#141824",
            fg="#00E5FF",
            selectcolor="#0B0E14",
            activebackground="#141824",
            font=("Segoe UI", 9, "bold"),
        ).pack(side=tk.LEFT, padx=(0, 10))
        tk.Radiobutton(
            mode_row,
            text="Riil (Exness-MT5Real)",
            variable=self.var_acc_mode,
            value="REAL",
            command=self._on_account_mode_changed,
            bg="#141824",
            fg="#00E676",
            selectcolor="#0B0E14",
            activebackground="#141824",
            font=("Segoe UI", 9, "bold"),
        ).pack(side=tk.LEFT)

        self._add_row(conn_box, "Login Akun MT5:", self.var_login, 1)
        self._add_row(conn_box, "Password Trading:", self.var_password, 2, show="*")

        tk.Label(conn_box, text="Server Exness:", bg="#141824", fg="#94A3B8", font=("Segoe UI", 9)).grid(
            row=3, column=0, sticky="w", pady=4
        )
        self.server_cb = ttk.Combobox(
            conn_box,
            textvariable=self.var_server,
            values=EXNESS_DEMO_SERVERS + EXNESS_REAL_SERVERS,
            width=28,
        )
        self.server_cb.grid(row=3, column=1, sticky="ew", pady=4)

        self._add_row(conn_box, "Path MT5 (opsional):", self.var_mt5_path, 4)
        self._add_row(conn_box, "Token Bridge Android:", self.var_bridge_token, 5)

        btn_row = tk.Frame(conn_box, bg="#141824")
        btn_row.grid(row=6, column=0, columnspan=2, sticky="ew", pady=(10, 4))

        tk.Button(
            btn_row,
            text="Login & Hubungkan Exness",
            command=self.connect_mt5_from_ui,
            bg="#00E5FF",
            fg="#0B0E14",
            font=("Segoe UI", 9, "bold"),
            relief=tk.FLAT,
            padx=12,
            pady=6,
        ).pack(side=tk.LEFT, fill=tk.X, expand=True, padx=(0, 6))

        tk.Button(
            btn_row,
            text="Sinkron Histori MT5",
            command=self.sync_mt5_history,
            bg="#262C42",
            fg="#F1F5F9",
            font=("Segoe UI", 9, "bold"),
            relief=tk.FLAT,
            padx=10,
            pady=6,
        ).pack(side=tk.LEFT)

        self.acc_info_lbl = tk.Label(
            conn_box,
            text="Balance: - | Equity: - | Free Margin: -",
            bg="#141824",
            fg="#00E676",
            font=("Consolas", 9, "bold"),
        )
        self.acc_info_lbl.grid(row=7, column=0, columnspan=2, sticky="w", pady=(6, 2))

        lan_ip = get_local_lan_ip()
        self.bridge_info_lbl = tk.Label(
            conn_box,
            text=f"URL Bridge untuk Android App: http://{lan_ip}:{self.bridge_port} (Aktif)",
            bg="#141824",
            fg="#FFB300",
            font=("Consolas", 9, "bold"),
        )
        self.bridge_info_lbl.grid(row=8, column=0, columnspan=2, sticky="w", pady=(2, 0))

        # Right: Strategy & Exness Market Controls
        bot_box = tk.LabelFrame(
            top_frame,
            text=" 2. Pasar Broker Exness & Bot AI ",
            bg="#141824",
            fg="#00E676",
            font=("Segoe UI", 10, "bold"),
            padx=12,
            pady=10,
        )
        bot_box.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)

        self.var_symbol = tk.StringVar(value="BTCUSDm")
        self.var_strategy = tk.StringVar(value="VOLATILITY_BREAKOUT")
        self.var_lot = tk.StringVar(value="0.01")
        self.var_sl_atr = tk.StringVar(value="1.5")
        self.var_tp_atr = tk.StringVar(value="2.5")
        self.var_gemini_key = tk.StringVar(value=os.getenv("GEMINI_API_KEY", ""))

        tk.Label(bot_box, text="Pasar Exness:", bg="#141824", fg="#94A3B8", font=("Segoe UI", 9)).grid(
            row=0, column=0, sticky="w", pady=4
        )
        symbol_cb = ttk.Combobox(bot_box, textvariable=self.var_symbol, values=EXNESS_MARKETS, width=26)
        symbol_cb.grid(row=0, column=1, sticky="ew", pady=4)

        tk.Label(bot_box, text="Strategi AI:", bg="#141824", fg="#94A3B8", font=("Segoe UI", 9)).grid(
            row=1, column=0, sticky="w", pady=4
        )
        strat_cb = ttk.Combobox(
            bot_box,
            textvariable=self.var_strategy,
            values=["VOLATILITY_BREAKOUT", "GRID_TRADING"],
            state="readonly",
            width=26,
        )
        strat_cb.grid(row=1, column=1, sticky="ew", pady=4)

        self._add_row(bot_box, "Lot Size:", self.var_lot, 2)
        self._add_row(bot_box, "SL Multiplier (x ATR):", self.var_sl_atr, 3)
        self._add_row(bot_box, "TP Multiplier (x ATR):", self.var_tp_atr, 4)
        self._add_row(bot_box, "Gemini API Key (opsional):", self.var_gemini_key, 5, show="*")

        ctrl_row = tk.Frame(bot_box, bg="#141824")
        ctrl_row.grid(row=6, column=0, columnspan=2, sticky="ew", pady=(10, 0))

        self.btn_start = tk.Button(
            ctrl_row,
            text="▶ Jalankan AI Bot",
            command=self.toggle_bot,
            bg="#00E676",
            fg="#0B0E14",
            font=("Segoe UI", 9, "bold"),
            relief=tk.FLAT,
            padx=12,
            pady=6,
        )
        self.btn_start.pack(side=tk.LEFT, fill=tk.X, expand=True, padx=(0, 6))

        tk.Button(
            ctrl_row,
            text="✖ Tutup Semua Posisi",
            command=self.emergency_close_all,
            bg="#FF3B69",
            fg="#FFFFFF",
            font=("Segoe UI", 9, "bold"),
            relief=tk.FLAT,
            padx=10,
            pady=6,
        ).pack(side=tk.LEFT)

        # Bottom: Live Log Console
        log_box = tk.LabelFrame(
            self.tab_terminal,
            text=" Log Real-Time Exness MT5 & Bridge Android ",
            bg="#141824",
            fg="#94A3B8",
            font=("Segoe UI", 9, "bold"),
            padx=8,
            pady=6,
        )
        log_box.pack(fill=tk.BOTH, expand=True, pady=(8, 0))

        self.log_text = tk.Text(
            log_box,
            bg="#0B0E14",
            fg="#E2E8F0",
            font=("Consolas", 9),
            height=13,
            state=tk.DISABLED,
            borderwidth=0,
        )
        self.log_text.pack(fill=tk.BOTH, expand=True)

    def _on_account_mode_changed(self) -> None:
        if self.var_acc_mode.get() == "REAL":
            self.server_cb.configure(values=EXNESS_REAL_SERVERS)
            if "Trial" in self.var_server.get() or not self.var_server.get():
                self.var_server.set("Exness-MT5Real")
        else:
            self.server_cb.configure(values=EXNESS_DEMO_SERVERS)
            if "Real" in self.var_server.get() or not self.var_server.get():
                self.var_server.set("Exness-MT5Trial")

    def _build_history_tab(self) -> None:
        toolbar = tk.Frame(self.tab_history, bg="#141824", pady=8, padx=10)
        toolbar.pack(fill=tk.X, pady=(4, 8))

        self.hist_summary_lbl = tk.Label(
            toolbar,
            text="Total Trade AI: 0 | Win Rate: 0.0% | Total PnL: $0.00",
            bg="#141824",
            fg="#00E676",
            font=("Consolas", 10, "bold"),
        )
        self.hist_summary_lbl.pack(side=tk.LEFT)

        self.var_hist_filter = tk.StringVar(value="ALL")
        filter_cb = ttk.Combobox(
            toolbar,
            textvariable=self.var_hist_filter,
            values=["ALL", "VOLATILITY_BREAKOUT", "GRID_TRADING", "ANDROID_EXNESS_AI"],
            state="readonly",
            width=22,
        )
        filter_cb.pack(side=tk.RIGHT, padx=(6, 0))
        filter_cb.bind("<<ComboboxSelected>>", lambda _e: self.refresh_history_table())

        tk.Button(
            toolbar,
            text="Export CSV",
            command=self.export_history_csv,
            bg="#00E5FF",
            fg="#0B0E14",
            font=("Segoe UI", 9, "bold"),
            relief=tk.FLAT,
            padx=10,
            pady=4,
        ).pack(side=tk.RIGHT, padx=6)

        tk.Button(
            toolbar,
            text="Refresh & Sinkron MT5",
            command=self.sync_mt5_history,
            bg="#262C42",
            fg="#F1F5F9",
            font=("Segoe UI", 9, "bold"),
            relief=tk.FLAT,
            padx=10,
            pady=4,
        ).pack(side=tk.RIGHT)

        cols = (
            "ticket",
            "waktu",
            "server",
            "simbol",
            "strategi",
            "aksi",
            "lot",
            "entry",
            "exit",
            "pnl",
            "status",
            "alasan_ai",
        )
        self.tree = ttk.Treeview(self.tab_history, columns=cols, show="headings")
        headers = {
            "ticket": ("Ticket", 75),
            "waktu": ("Waktu", 130),
            "server": ("Server", 110),
            "simbol": ("Simbol", 80),
            "strategi": ("Strategi", 130),
            "aksi": ("Aksi", 55),
            "lot": ("Lot", 50),
            "entry": ("Entry", 80),
            "exit": ("Exit", 80),
            "pnl": ("PnL ($)", 75),
            "status": ("Status", 65),
            "alasan_ai": ("Alasan / Sinyal AI", 260),
        }
        for key, (label, width) in headers.items():
            self.tree.heading(key, text=label)
            self.tree.column(key, width=width, anchor="w")

        scroll_y = ttk.Scrollbar(self.tab_history, orient=tk.VERTICAL, command=self.tree.yview)
        self.tree.configure(yscrollcommand=scroll_y.set)
        self.tree.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        scroll_y.pack(side=tk.RIGHT, fill=tk.Y)

    def _add_row(
        self,
        parent: tk.Widget,
        label: str,
        var: tk.StringVar,
        row: int,
        show: str = "",
    ) -> None:
        tk.Label(parent, text=label, bg="#141824", fg="#94A3B8", font=("Segoe UI", 9)).grid(
            row=row, column=0, sticky="w", pady=4
        )
        tk.Entry(
            parent,
            textvariable=var,
            bg="#0B0E14",
            fg="#F1F5F9",
            insertbackground="#00E5FF",
            relief=tk.FLAT,
            show=show,
            width=30,
        ).grid(row=row, column=1, sticky="ew", pady=4, ipady=3)

    def log(self, msg: str) -> None:
        ts = datetime.now().strftime("%H:%M:%S")
        line = f"[{ts}] {msg}\n"

        def _append() -> None:
            self.log_text.configure(state=tk.NORMAL)
            self.log_text.insert(tk.END, line)
            self.log_text.see(tk.END)
            self.log_text.configure(state=tk.DISABLED)

        self.root.after(0, _append)

    def connect_mt5_credentials(
        self,
        login_str: str,
        password: str,
        server: str,
        path: str = "",
    ) -> Tuple[bool, str, Optional[Dict[str, Any]]]:
        if not MT5_AVAILABLE:
            return False, "Library MetaTrader5 belum terpasang di PC Windows.", None

        with self._mt5_lock:
            init_kwargs: Dict[str, object] = {}
            if path.strip():
                init_kwargs["path"] = path.strip()
            if login_str.strip().isdigit() and password and server.strip():
                init_kwargs["login"] = int(login_str.strip())
                init_kwargs["password"] = password
                init_kwargs["server"] = server.strip()

            ok = mt5.initialize(**init_kwargs)
            if not ok:
                err = mt5.last_error()
                return False, f"Gagal inisialisasi MT5: {err}", None

            acc = mt5.account_info()
            if acc is None:
                return False, "Terminal MT5 terbuka tetapi gagal login ke akun trading.", None

            positions = mt5.positions_get() or []
            mode_tag = "REAL" if ("Real" in acc.server or acc.trade_mode == mt5.ACCOUNT_TRADE_MODE_REAL) else "DEMO"
            info_dict = {
                "login": str(acc.login),
                "server": acc.server,
                "account_mode": mode_tag,
                "balance": float(acc.balance),
                "equity": float(acc.equity),
                "margin_free": float(acc.margin_free),
                "open_positions": len(positions),
            }

            self.is_connected = True

        def _update_ui() -> None:
            self.status_lbl.configure(
                text=f"● Terhubung: #{info_dict['login']} ({info_dict['server']} - {info_dict['account_mode']})",
                fg="#00E676" if info_dict["account_mode"] == "REAL" else "#00E5FF",
            )
            self.acc_info_lbl.configure(
                text=f"Balance: ${info_dict['balance']:,.2f} | Equity: ${info_dict['equity']:,.2f} | Free Margin: ${info_dict['margin_free']:,.2f}"
            )
            self.sync_mt5_history()

        self.root.after(0, _update_ui)
        self.log(f"Login Exness MT5 sukses: #{info_dict['login']} @ {info_dict['server']} [{mode_tag}]")
        return True, "Connected", info_dict

    def connect_mt5_from_ui(self) -> None:
        ok, msg, _info = self.connect_mt5_credentials(
            self.var_login.get(),
            self.var_password.get(),
            self.var_server.get(),
            self.var_mt5_path.get(),
        )
        if not ok:
            self.log(msg)
            messagebox.showerror("Koneksi Exness MT5 Gagal", f"{msg}\nPastikan MT5 Desktop terbuka di Windows.")

    def start_android_bridge(self) -> None:
        app_ref = self

        class BridgeHandler(BaseHTTPRequestHandler):
            def log_message(self, format: str, *args: object) -> None:
                return

            def _send_json(self, code: int, payload: Any) -> None:
                body = json.dumps(payload).encode("utf-8")
                self.send_response(code)
                self.send_header("Content-Type", "application/json; charset=utf-8")
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)

            def _is_authorized(self) -> bool:
                expected = app_ref.var_bridge_token.get().strip() or app_ref.bridge_token
                auth_header = self.headers.get("Authorization", "")
                return auth_header == f"Bearer {expected}"

            def _read_json_body(self) -> Dict[str, Any]:
                length = int(self.headers.get("Content-Length", "0"))
                if length <= 0:
                    return {}
                raw = self.rfile.read(length).decode("utf-8")
                return json.loads(raw) if raw else {}

            def do_GET(self) -> None:
                if not self._is_authorized():
                    self._send_json(401, {"detail": "Token Bridge tidak valid"})
                    return

                if self.path == "/health":
                    if not MT5_AVAILABLE or not app_ref.is_connected:
                        self._send_json(503, {"detail": "PC belum login ke MT5 Exness"})
                        return
                    with app_ref._mt5_lock:
                        acc = mt5.account_info()
                        positions = mt5.positions_get() or []
                    if acc is None:
                        self._send_json(500, {"detail": "account_info() tidak tersedia"})
                        return
                    mode_tag = "REAL" if ("Real" in acc.server or acc.trade_mode == mt5.ACCOUNT_TRADE_MODE_REAL) else "DEMO"
                    self._send_json(
                        200,
                        {
                            "login": str(acc.login),
                            "server": acc.server,
                            "account_mode": mode_tag,
                            "balance": float(acc.balance),
                            "equity": float(acc.equity),
                            "margin_free": float(acc.margin_free),
                            "open_positions": len(positions),
                        },
                    )
                    return

                if self.path == "/quotes":
                    if not MT5_AVAILABLE or not app_ref.is_connected:
                        self._send_json(200, {"quotes": []})
                        return
                    quotes = []
                    with app_ref._mt5_lock:
                        for sym in EXNESS_MARKETS:
                            mt5.symbol_select(sym, True)
                            tick = mt5.symbol_info_tick(sym)
                            if tick is not None:
                                quotes.append(
                                    {
                                        "symbol": sym,
                                        "bid": float(tick.bid),
                                        "ask": float(tick.ask),
                                        "last": float(tick.last or tick.bid),
                                    }
                                )
                    self._send_json(200, {"quotes": quotes})
                    return

                if self.path == "/history":
                    rows = app_ref.store.fetch_all("ALL")[:50]
                    items = [
                        {
                            "id": r[0],
                            "ticket": r[1],
                            "timestamp": r[2],
                            "broker_server": r[3],
                            "symbol": r[4],
                            "strategy": r[5],
                            "action": r[6],
                            "volume": r[7],
                            "entry_price": r[8],
                            "exit_price": r[9],
                            "pnl": r[10],
                            "status": r[11],
                            "ai_reason": r[12],
                        }
                        for r in rows
                    ]
                    self._send_json(200, {"history": items})
                    return

                self._send_json(404, {"detail": "Endpoint tidak ditemukan"})

            def do_POST(self) -> None:
                if not self._is_authorized():
                    self._send_json(401, {"detail": "Token Bridge tidak valid"})
                    return

                try:
                    data = self._read_json_body()
                except Exception as exc:
                    self._send_json(400, {"detail": f"JSON tidak valid: {exc}"})
                    return

                if self.path == "/connect":
                    login_val = str(data.get("login", "")).strip()
                    password_val = str(data.get("password", ""))
                    server_val = str(data.get("server", "Exness-MT5Trial")).strip()
                    if login_val:
                        app_ref.root.after(0, lambda: app_ref.var_login.set(login_val))
                    if server_val:
                        app_ref.root.after(0, lambda: app_ref.var_server.set(server_val))
                        mode = "REAL" if "Real" in server_val else "DEMO"
                        app_ref.root.after(0, lambda m=mode: app_ref.var_acc_mode.set(m))

                    ok, msg, info = app_ref.connect_mt5_credentials(login_val, password_val, server_val)
                    if not ok or info is None:
                        self._send_json(400, {"detail": msg})
                        return
                    self._send_json(200, info)
                    return

                if self.path == "/order":
                    if not MT5_AVAILABLE or not app_ref.is_connected:
                        self._send_json(400, {"detail": "MT5 Exness belum terhubung"})
                        return
                    sym = str(data.get("symbol", "BTCUSDm")).strip()
                    action = str(data.get("action", "BUY")).upper()
                    volume = float(data.get("volume", 0.01))
                    sl_opt = data.get("sl")
                    tp_opt = data.get("tp")
                    comment = str(data.get("comment", "CryptoQuant Android"))
                    ok, msg, order_info = app_ref.execute_direct_order(
                        symbol=sym,
                        strategy="ANDROID_EXNESS_AI",
                        action=action,
                        volume=volume,
                        sl_override=float(sl_opt) if sl_opt else None,
                        tp_override=float(tp_opt) if tp_opt else None,
                        reason=comment,
                    )
                    if not ok or order_info is None:
                        self._send_json(400, {"detail": msg})
                        return
                    self._send_json(200, order_info)
                    return

                if self.path == "/close_all":
                    closed = app_ref.close_all_positions_sync()
                    self._send_json(200, {"closed_count": closed})
                    return

                self._send_json(404, {"detail": "Endpoint tidak ditemukan"})

        try:
            self.bridge_server = ThreadingHTTPServer(("0.0.0.0", self.bridge_port), BridgeHandler)
            thread = threading.Thread(target=self.bridge_server.serve_forever, daemon=True)
            thread.start()
            lan_ip = get_local_lan_ip()
            self.log(f"Android Bridge Server siap di http://{lan_ip}:{self.bridge_port} (Token: {self.bridge_token})")
        except Exception as exc:
            self.log(f"Gagal memulai Android Bridge di port {self.bridge_port}: {exc}")

    def sync_mt5_history(self) -> None:
        if MT5_AVAILABLE and self.is_connected:
            with self._mt5_lock:
                from_date = datetime.now() - timedelta(days=30)
                deals = mt5.history_deals_get(from_date, datetime.now())
                if deals:
                    closed_updates: List[Tuple[int, float, float]] = []
                    for deal in deals:
                        if deal.entry == mt5.DEAL_ENTRY_OUT:
                            closed_updates.append((int(deal.position_id), float(deal.price), float(deal.profit)))
                    self.store.sync_closed_deals(closed_updates)
                acc = mt5.account_info()
                if acc:
                    self.acc_info_lbl.configure(
                        text=f"Balance: ${acc.balance:,.2f} | Equity: ${acc.equity:,.2f} | Free Margin: ${acc.margin_free:,.2f}"
                    )
        self.refresh_history_table()

    def refresh_history_table(self) -> None:
        for item in self.tree.get_children():
            self.tree.delete(item)

        rows = self.store.fetch_all(self.var_hist_filter.get())
        total_pnl = 0.0
        closed_count = 0
        win_count = 0

        for row in rows:
            _id, ticket, ts, srv, sym, strat, act, vol, entry_px, exit_px, pnl, status, reason = row
            pnl_val = float(pnl or 0.0)
            total_pnl += pnl_val
            if status == "CLOSED":
                closed_count += 1
                if pnl_val > 0:
                    win_count += 1
            self.tree.insert(
                "",
                tk.END,
                values=(
                    ticket,
                    ts,
                    srv or "-",
                    sym,
                    strat,
                    act,
                    f"{vol:.2f}",
                    f"{entry_px:.4f}",
                    f"{exit_px:.4f}" if exit_px else "-",
                    f"{pnl_val:+.2f}",
                    status,
                    reason,
                ),
            )

        win_rate = (win_count / closed_count * 100.0) if closed_count > 0 else 0.0
        self.hist_summary_lbl.configure(
            text=f"Total Trade AI: {len(rows)} | Win Rate: {win_rate:.1f}% | Total PnL: ${total_pnl:+.2f}"
        )

    def export_history_csv(self) -> None:
        rows = self.store.fetch_all("ALL")
        if not rows:
            messagebox.showinfo("Export CSV", "Belum ada histori trading AI untuk diekspor.")
            return
        file_path = filedialog.asksaveasfilename(
            defaultextension=".csv",
            filetypes=[("CSV files", "*.csv")],
            initialfile=f"ai_trade_history_{datetime.now().strftime('%Y%m%d')}.csv",
        )
        if not file_path:
            return
        with open(file_path, "w", newline="", encoding="utf-8") as f:
            writer = csv.writer(f)
            writer.writerow(
                ["ID", "Ticket", "Waktu", "Server", "Simbol", "Strategi", "Aksi", "Lot", "Entry", "Exit", "PnL", "Status", "Alasan_AI"]
            )
            writer.writerows(rows)
        messagebox.showinfo("Export Berhasil", f"Histori tersimpan ke:\n{file_path}")

    def toggle_bot(self) -> None:
        if not self.is_connected:
            messagebox.showwarning("Belum Terhubung", "Login ke akun Exness (Demo / Riil) terlebih dahulu.")
            return
        if self.is_bot_running:
            self.is_bot_running = False
            self.btn_start.configure(text="▶ Jalankan AI Bot", bg="#00E676")
            self.log("AI Bot dihentikan.")
        else:
            self.is_bot_running = True
            self.btn_start.configure(text="⏸ Hentikan AI Bot", bg="#FFB300")
            self.log(f"AI Bot aktif untuk {self.var_symbol.get()} [{self.var_strategy.get()}]")
            self.bot_thread = threading.Thread(target=self._bot_loop, daemon=True)
            self.bot_thread.start()

    def _bot_loop(self) -> None:
        while self.is_bot_running and self.is_connected:
            try:
                symbol = self.var_symbol.get().strip()
                strategy = self.var_strategy.get()
                lot = float(self.var_lot.get() or 0.01)
                sl_mult = float(self.var_sl_atr.get() or 1.5)
                tp_mult = float(self.var_tp_atr.get() or 2.5)
                gemini_key = self.var_gemini_key.get()

                with self._mt5_lock:
                    mt5.symbol_select(symbol, True)
                    rates = mt5.copy_rates_from_pos(symbol, mt5.TIMEFRAME_M5, 0, 80)
                    open_positions = mt5.positions_get(symbol=symbol) or []

                if rates is None or len(rates) < 25:
                    self.log(f"Menunggu data candlestick MT5 untuk {symbol}...")
                    time.sleep(5)
                    continue

                closes = [float(r["close"]) for r in rates]
                highs = [float(r["high"]) for r in rates]
                lows = [float(r["low"]) for r in rates]
                ind = compute_indicators(closes, highs, lows)

                action: Optional[str] = None
                if strategy == "VOLATILITY_BREAKOUT":
                    if ind["price"] > ind["bb_upper"] and ind["ema20"] >= ind["ema50"] and ind["rsi"] < 75:
                        action = "BUY"
                    elif ind["price"] < ind["bb_lower"] and ind["ema20"] <= ind["ema50"] and ind["rsi"] > 25:
                        action = "SELL"
                else:
                    if ind["price"] <= ind["bb_lower"] and ind["rsi"] < 42:
                        action = "BUY"
                    elif ind["price"] >= ind["bb_upper"] and ind["rsi"] > 58:
                        action = "SELL"

                cooldown_ok = (time.time() - self.last_trade_time) > 60
                if action and len(open_positions) < 3 and cooldown_ok:
                    approved, reason = query_gemini_confirmation(gemini_key, symbol, strategy, action, ind)
                    if approved:
                        self.execute_direct_order(
                            symbol=symbol,
                            strategy=strategy,
                            action=action,
                            volume=lot,
                            atr=ind["atr"],
                            sl_mult=sl_mult,
                            tp_mult=tp_mult,
                            reason=reason,
                        )
                        self.last_trade_time = time.time()
                    else:
                        self.log(f"Sinyal {action} {symbol} ditahan AI: {reason}")
                else:
                    self.log(
                        f"Scan {symbol}: Price={ind['price']:.4f} | RSI={ind['rsi']:.1f} | "
                        f"BB=[{ind['bb_lower']:.2f}, {ind['bb_upper']:.2f}] | Posisi={len(open_positions)}"
                    )

                self.root.after(0, self.sync_mt5_history)
            except Exception as exc:
                self.log(f"Error loop bot: {exc}")

            time.sleep(5)

    def _resolve_filling_mode(self, symbol: str) -> int:
        info = mt5.symbol_info(symbol)
        if info is not None:
            if info.filling_mode & 2:
                return mt5.ORDER_FILLING_IOC
            if info.filling_mode & 1:
                return mt5.ORDER_FILLING_FOK
        return mt5.ORDER_FILLING_RETURN

    def execute_direct_order(
        self,
        symbol: str,
        strategy: str,
        action: str,
        volume: float,
        atr: float = 0.0,
        sl_mult: float = 1.5,
        tp_mult: float = 2.5,
        sl_override: Optional[float] = None,
        tp_override: Optional[float] = None,
        reason: str = "Eksekusi AI",
    ) -> Tuple[bool, str, Optional[Dict[str, Any]]]:
        with self._mt5_lock:
            mt5.symbol_select(symbol, True)
            tick = mt5.symbol_info_tick(symbol)
            info = mt5.symbol_info(symbol)
            if tick is None:
                msg = f"Simbol {symbol} tidak ditemukan atau tick kosong di Exness MT5"
                self.log(msg)
                return False, msg, None

            digits = info.digits if info else 5
            is_buy = action == "BUY"
            price = float(tick.ask if is_buy else tick.bid)

            sl_val = sl_override if sl_override is not None else (
                (price - (atr * sl_mult) if is_buy else price + (atr * sl_mult)) if atr > 0 else 0.0
            )
            tp_val = tp_override if tp_override is not None else (
                (price + (atr * tp_mult) if is_buy else price - (atr * tp_mult)) if atr > 0 else 0.0
            )

            request: Dict[str, Any] = {
                "action": mt5.TRADE_ACTION_DEAL,
                "symbol": symbol,
                "volume": volume,
                "type": mt5.ORDER_TYPE_BUY if is_buy else mt5.ORDER_TYPE_SELL,
                "price": price,
                "deviation": 20,
                "magic": 20261115,
                "comment": f"CryptoQuantAI:{strategy[:10]}",
                "type_time": mt5.ORDER_TIME_GTC,
                "type_filling": self._resolve_filling_mode(symbol),
            }
            if sl_val > 0:
                request["sl"] = round(sl_val, digits)
            if tp_val > 0:
                request["tp"] = round(tp_val, digits)

            res = mt5.order_send(request)
            if res is None or res.retcode != mt5.TRADE_RETCODE_DONE:
                err = res.comment if res else mt5.last_error()
                msg = f"Order {action} {symbol} gagal: {err}"
                self.log(msg)
                return False, msg, None

            acc = mt5.account_info()
            server_name = acc.server if acc else self.var_server.get()
            ticket_id = int(getattr(res, "order", 0) or getattr(res, "deal", 0))
            exec_price = float(res.price)

        self.store.record_trade(
            ticket=ticket_id,
            broker_server=server_name,
            symbol=symbol,
            strategy=strategy,
            action=action,
            volume=volume,
            entry_price=exec_price,
            sl=round(sl_val, digits) if sl_val > 0 else 0.0,
            tp=round(tp_val, digits) if tp_val > 0 else 0.0,
            ai_reason=reason,
        )
        self.log(f"EKSEKUSI SUKSES #{ticket_id}: {action} {volume} {symbol} @ {exec_price} | {reason}")
        self.root.after(0, self.refresh_history_table)
        return True, "Executed", {"ticket": ticket_id, "price": exec_price, "message": "Executed"}

    def close_all_positions_sync(self) -> int:
        if not (MT5_AVAILABLE and self.is_connected):
            return 0
        closed = 0
        with self._mt5_lock:
            positions = mt5.positions_get() or []
            for pos in positions:
                tick = mt5.symbol_info_tick(pos.symbol)
                if tick is None:
                    continue
                close_type = mt5.ORDER_TYPE_SELL if pos.type == mt5.POSITION_TYPE_BUY else mt5.ORDER_TYPE_BUY
                price = tick.bid if pos.type == mt5.POSITION_TYPE_BUY else tick.ask
                res = mt5.order_send(
                    {
                        "action": mt5.TRADE_ACTION_DEAL,
                        "symbol": pos.symbol,
                        "volume": pos.volume,
                        "type": close_type,
                        "position": pos.ticket,
                        "price": price,
                        "deviation": 20,
                        "magic": 20261115,
                        "comment": "EmergencyCloseAll",
                        "type_filling": self._resolve_filling_mode(pos.symbol),
                    }
                )
                if res and res.retcode == mt5.TRADE_RETCODE_DONE:
                    closed += 1
        self.log(f"EMERGENCY CLOSE ALL: {closed} posisi ditutup.")
        self.root.after(0, self.sync_mt5_history)
        return closed

    def emergency_close_all(self) -> None:
        self.is_bot_running = False
        self.btn_start.configure(text="▶ Jalankan AI Bot", bg="#00E676")
        self.close_all_positions_sync()


if __name__ == "__main__":
    app_root = tk.Tk()
    ExnessMt5PcApp(app_root)
    app_root.mainloop()
