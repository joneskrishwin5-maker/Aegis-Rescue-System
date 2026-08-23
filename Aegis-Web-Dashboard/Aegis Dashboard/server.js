const express = require('express');
const app = express();
const http = require('http').createServer(app);
const io = require('socket.io')(http);

// Middleware
app.use(express.json());
app.use(express.static(__dirname));

// THE BOUNCER: Remembers which messages we have already processed
const processedAlerts = new Set();

app.post('/api/sos-alert', (req, res) => {
    try {
        const data = req.body;
        
        // 1. Check if we already received this exact SOS
        if (data.msgId && processedAlerts.has(data.msgId)) {
            console.log(`♻️ Ignored duplicate SOS from Gateway: ${data.gatewayNode}`);
            return res.status(200).json({ status: "Ignored duplicate" });
        }

        // 2. If it is new, add it to the memory
        if (data.msgId) {
            processedAlerts.add(data.msgId);
        }

        // 3. Send it to the Dashboard UI
        console.log(`🚨 New SOS Alert Processed! Gateway: ${data.gatewayNode}`);
        io.emit('new_sos_alert', data);
        
        // 4. Safely close the HTTP request
        return res.status(200).json({ status: "Success" });

    } catch (error) {
        console.error("❌ Server Crash Prevented:", error);
        return res.status(500).json({ status: "Server error prevented" });
    }
});

// CLOUD READY: Uses the host's assigned port, or defaults to 3000 locally
const PORT = process.env.PORT || 3000;
http.listen(PORT, () => {
    console.log(`🚀 Aegis Dashboard running safely on port ${PORT}`);
});