// Cliente STOMP de prueba: se conecta al backend, se suscribe a /topic/ventas
// y /topic/pedidos, y escribe cada evento recibido en stomp_events.log.
import { Client } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import fs from "node:fs";

const BASE = process.argv[2] || "http://localhost:8081";
const OUT = process.argv[3] || "stomp_events.log";
fs.writeFileSync(OUT, "");

const login = await fetch(`${BASE}/api/auth/login`, {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ usuario: "admin", contrasena: "admin123" }),
});
const token = (await login.json()).data.accessToken;

const client = new Client({
  webSocketFactory: () => new SockJS(`${BASE}/ws`),
  connectHeaders: { Authorization: `Bearer ${token}` },
  reconnectDelay: 0,
  onConnect: () => {
    fs.appendFileSync(OUT, "CONNECTED\n");
    client.subscribe("/topic/ventas", (m) =>
      fs.appendFileSync(OUT, "VENTAS " + m.body + "\n"),
    );
    client.subscribe("/topic/pedidos", (m) =>
      fs.appendFileSync(OUT, "PEDIDOS " + m.body + "\n"),
    );
  },
  onStompError: (f) => fs.appendFileSync(OUT, "STOMP_ERROR " + f.body + "\n"),
  onWebSocketError: () => fs.appendFileSync(OUT, "WS_ERROR\n"),
});
client.activate();

setTimeout(() => {
  client.deactivate();
  process.exit(0);
}, 90000);
