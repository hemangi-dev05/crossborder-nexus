
import React, { useState, useEffect } from 'react';

const API_URL = 'http://localhost:8080/api/v1/payments/initiate';

const initialForm = {
    debtorName: 'Alice Vance',
    debtorIban: 'GB29UKLN123456',
    debtorBic: 'UKLNBK2L',
    creditorName: 'Satoshi Nakamoto',
    creditorIban: 'IN89RAZO987654',
    creditorBic: 'RAZOBKIN',
    amount: '12500.00',
    currency: 'INR'
};

export default function App() {
    const [form, setForm] = useState(initialForm);

    const [logs, setLogs] = useState([
        {
            time: new Date().toLocaleTimeString(),
            text: 'Cross-Border Control Tower initialized. Monitoring active.',
            type: 'info'
        }
    ]);

    const [loading, setLoading] = useState(false);

    const [routing, setRouting] = useState({
        badge: 'ISO 20022',
        icon: '🏢',
        reason: ''
    });

    // Payment routing logic
    useEffect(() => {
        const amount = Number(form.amount) || 0;

        if (amount >= 10000 || form.currency !== 'INR') {
            setRouting({
                badge: 'ISO 20022',
                icon: '🏢',
                reason: 'High-value or foreign-currency payment route.'
            });
        } else {
            setRouting({
                badge: 'Razorpay',
                icon: '⚡',
                reason: 'Domestic payment demonstration route.'
            });
        }
    }, [form.amount, form.currency]);

    // Add activity logs
    const addLog = (text, type = 'info') => {
        setLogs(prev => [
            ...prev,
            {
                time: new Date().toLocaleTimeString(),
                text,
                type
            }
        ]);
    };

    // Handle form changes
    const handleChange = (e) => {
        const { id, value } = e.target;

        setForm(prev => ({
            ...prev,
            [id]: value
        }));
    };

    // Clear logs
    const handleClearLogs = () => {
        setLogs([
            {
                time: new Date().toLocaleTimeString(),
                text: 'Activity logs cleared by operator.',
                type: 'info'
            }
        ]);
    };

    // Generate transaction ID
    const generateTransactionId = () => {
        return `TXN_${Date.now()}`;
    };

    // Generate UETR for simulation
    const generateUetr = () => {
        return window.crypto?.randomUUID?.() ||
            `${Date.now()}-${Math.random().toString(16).slice(2)}`;
    };

    // Submit payment
    const handleSubmit = async (e) => {
        e.preventDefault();

        if (loading) return;

        const amount = Number(form.amount);

        if (!Number.isFinite(amount) || amount <= 0) {
            addLog('Invalid payment amount.', 'error');
            return;
        }

        setLoading(true);

        addLog(`Preparing payment for ${form.debtorName}...`);

        const payload = {
            ...form,
            amount,
            transactionId: generateTransactionId()
        };

        try {
            addLog(`Sending payment request to ${API_URL}`);

            const response = await fetch(API_URL, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(payload)
            });

            const responseText = await response.text();

            let data = {};

            try {
                data = responseText ? JSON.parse(responseText) : {};
            } catch {
                data = { message: responseText };
            }

            if (!response.ok) {
                addLog(`Backend rejected payment. HTTP ${response.status}`, 'error');
                addLog(data.message || 'Payment processing failed.', 'error');
                return;
            }

            addLog('Backend successfully processed the request.', 'success');
            addLog(`Transaction status: ${data.status || 'PROCESSED'}`, 'success');
            addLog(data.message || 'Backend response received.', 'success');

        } catch (error) {
            addLog('Backend connection unavailable.', 'warning');
            addLog('Starting frontend simulation mode. No real payment is being processed.');

            await new Promise(resolve => setTimeout(resolve, 600));

            const isBlacklisted =
                form.debtorName.toLowerCase().includes('blacklisted') ||
                form.creditorName.toLowerCase().includes('blacklisted');

            if (isBlacklisted) {
                addLog('[SIMULATION] Compliance screening flagged the transaction.', 'error');
            } else {
                addLog(
                    `[SIMULATION] Payment flow completed. UETR: ${generateUetr()}`,
                    'success'
                );

                addLog(
                    routing.badge === 'ISO 20022'
                        ? '[SIMULATION] ISO 20022 payment message mapping.'
                        : '[SIMULATION] Domestic payment routing.',
                    'success'
                );
            }
        } finally {
            setLoading(false);
        }
    };

    const logColor = {
        success: 'text-emerald-400',
        error: 'text-red-400',
        warning: 'text-amber-400',
        info: 'text-slate-300'
    };

    return (
        <div className="min-h-screen bg-slate-900 text-gray-100 font-sans flex flex-col">

            {/* Header */}
            <header className="border-b border-slate-800 p-4 bg-slate-900 sticky top-0 z-50">
                <div className="max-w-7xl mx-auto flex justify-between items-center flex-wrap gap-3">

                    <h1 className="text-lg font-bold text-emerald-400">
                        📡 CROSS-BORDER PAYMENT CONTROL TOWER
                    </h1>

                    <div className="text-xs text-emerald-400">
                        ● SYSTEM ONLINE
                    </div>

                </div>
            </header>

            {/* Main */}
            <main className="max-w-7xl mx-auto w-full px-4 py-8 flex-1">

                <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">

                    {/* Payment Form */}
                    <section className="lg:col-span-1 bg-slate-950 border border-slate-800 rounded-2xl p-6">

                        <h2 className="text-sm font-semibold mb-5 text-slate-200">
                            📥 Payment Initiation
                        </h2>

                        <form onSubmit={handleSubmit} className="space-y-4">

                            <div>
                                <label className="block text-xs text-slate-400 mb-1">
                                    Debtor Name (Sender)
                                </label>
                                <input
                                    id="debtorName"
                                    value={form.debtorName}
                                    onChange={handleChange}
                                    className="w-full bg-slate-900 border border-slate-700 rounded-lg p-2 text-sm"
                                    required
                                />
                            </div>

                            <div className="grid grid-cols-2 gap-3">

                                <div>
                                    <label className="block text-xs text-slate-400 mb-1">
                                        Debtor IBAN
                                    </label>
                                    <input
                                        id="debtorIban"
                                        value={form.debtorIban}
                                        onChange={handleChange}
                                        className="w-full bg-slate-900 border border-slate-700 rounded-lg p-2 text-sm"
                                        required
                                    />
                                </div>

                                <div>
                                    <label className="block text-xs text-slate-400 mb-1">
                                        Debtor BIC
                                    </label>
                                    <input
                                        id="debtorBic"
                                        value={form.debtorBic}
                                        onChange={handleChange}
                                        className="w-full bg-slate-900 border border-slate-700 rounded-lg p-2 text-sm"
                                        required
                                    />
                                </div>

                            </div>

                            <hr className="border-slate-800" />

                            <div>
                                <label className="block text-xs text-slate-400 mb-1">
                                    Creditor Name (Beneficiary)
                                </label>
                                <input
                                    id="creditorName"
                                    value={form.creditorName}
                                    onChange={handleChange}
                                    className="w-full bg-slate-900 border border-slate-700 rounded-lg p-2 text-sm"
                                    required
                                />
                            </div>

                            <div className="grid grid-cols-2 gap-3">

                                <div>
                                    <label className="block text-xs text-slate-400 mb-1">
                                        Creditor IBAN
                                    </label>
                                    <input
                                        id="creditorIban"
                                        value={form.creditorIban}
                                        onChange={handleChange}
                                        className="w-full bg-slate-900 border border-slate-700 rounded-lg p-2 text-sm"
                                        required
                                    />
                                </div>

                                <div>
                                    <label className="block text-xs text-slate-400 mb-1">
                                        Creditor BIC
                                    </label>
                                    <input
                                        id="creditorBic"
                                        value={form.creditorBic}
                                        onChange={handleChange}
                                        className="w-full bg-slate-900 border border-slate-700 rounded-lg p-2 text-sm"
                                        required
                                    />
                                </div>

                            </div>

                            <hr className="border-slate-800" />

                            <div className="grid grid-cols-3 gap-3 bg-slate-900 p-3 rounded-xl border border-slate-800">

                                <div className="col-span-2">
                                    <label className="block text-xs text-slate-400 mb-1">
                                        Settlement Amount
                                    </label>
                                    <input
                                        type="number"
                                        id="amount"
                                        min="0.01"
                                        step="0.01"
                                        value={form.amount}
                                        onChange={handleChange}
                                        className="w-full bg-slate-950 border border-slate-700 rounded-lg p-2 text-sm text-emerald-400"
                                        required
                                    />
                                </div>

                                <div>
                                    <label className="block text-xs text-slate-400 mb-1">
                                        Currency
                                    </label>
                                    <select
                                        id="currency"
                                        value={form.currency}
                                        onChange={handleChange}
                                        className="w-full bg-slate-950 border border-slate-700 rounded-lg p-2 text-sm"
                                    >
                                        <option value="INR">INR</option>
                                        <option value="USD">USD</option>
                                        <option value="EUR">EUR</option>
                                        <option value="GBP">GBP</option>
                                        <option value="JPY">JPY</option>
                                    </select>
                                </div>

                            </div>

                            {/* Routing */}
                            <div className="bg-slate-900 border border-slate-800 rounded-xl p-4">

                                <p className="text-xs text-slate-400 mb-2">
                                    SELECTED PAYMENT ROUTE
                                </p>

                                <h3 className="text-lg font-bold text-emerald-400">
                                    {routing.icon} {routing.badge}
                                </h3>

                                <p className="text-xs text-slate-400 mt-2">
                                    {routing.reason}
                                </p>

                            </div>

                            <button
                                type="submit"
                                disabled={loading}
                                className="w-full bg-emerald-500 hover:bg-emerald-400 disabled:bg-slate-700 text-slate-950 font-bold p-3 rounded-lg text-sm transition"
                            >
                                {loading ? 'PROCESSING...' : 'INITIATE PAYMENT →'}
                            </button>

                        </form>
                    </section>

                    {/* Dashboard */}
                    <section className="lg:col-span-2 space-y-6">

                        {/* Summary Cards */}
                        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">

                            <div className="bg-slate-950 border border-slate-800 rounded-xl p-5">
                                <p className="text-xs text-slate-400">
                                    CURRENT ROUTING
                                </p>
                                <h3 className="text-2xl text-emerald-400 font-bold mt-2">
                                    {routing.badge}
                                </h3>
                            </div>

                            <div className="bg-slate-950 border border-slate-800 rounded-xl p-5">
                                <p className="text-xs text-slate-400">
                                    ACTIVITY LOGS
                                </p>
                                <h3 className="text-2xl text-cyan-400 font-bold mt-2">
                                    {logs.length}
                                </h3>
                            </div>

                        </div>

                        {/* Activity Logs */}
                        <div className="bg-slate-950 border border-slate-800 rounded-2xl overflow-hidden">

                            <div className="flex justify-between items-center p-5 border-b border-slate-800">

                                <div>
                                    <h2 className="text-sm font-semibold">
                                        📊 Transaction Activity
                                    </h2>
                                    <p className="text-xs text-slate-500 mt-1">
                                        Payment processing logs
                                    </p>
                                </div>

                                <button
                                    type="button"
                                    onClick={handleClearLogs}
                                    className="text-xs text-slate-400 hover:text-red-400"
                                >
                                    CLEAR LOGS
                                </button>

                            </div>

                            <div className="p-5 space-y-3 min-h-64 max-h-96 overflow-y-auto font-mono">

                                {logs.map((log, index) => (
                                    <div
                                        key={index}
                                        className="flex gap-3 text-xs leading-relaxed"
                                    >
                                        <span className="text-slate-500 shrink-0">
                                            [{log.time}]
                                        </span>

                                        <span className={logColor[log.type] || logColor.info}>
                                            {log.text}
                                        </span>
                                    </div>
                                ))}

                            </div>

                            <div className="border-t border-slate-800 p-3 text-center">
                                <p className="text-xs text-slate-500">
                                    CONTROL TOWER ENGINE · MONITORING ACTIVE
                                </p>
                            </div>

                        </div>

                    </section>

                </div>
            </main>

            <footer className="border-t border-slate-800 p-4 text-center">
                <p className="text-xs text-slate-500">
                    CROSS-BORDER PAYMENT CONTROL TOWER · STARTER VERSION
                </p>
            </footer>

        </div>
    );
}
