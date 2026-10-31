/* Cliente da API e utilitários compartilhados pelas páginas. */

const api = axios.create({
  baseURL: "/api",
  timeout: 60000,
  headers: { "Content-Type": "application/json" },
});

/** Extrai uma mensagem legível de um erro do Axios. */
function errorMessage(err) {
  if (err.response && err.response.data && err.response.data.erro) return err.response.data.erro;
  if (err.code === "ECONNABORTED") return "A requisição demorou demais para responder.";
  if (!err.response) return "Não foi possível conectar ao servidor.";
  return `Erro ${err.response.status}`;
}

/** Busca o canal configurado no servidor (cacheado). */
let configPromise;
function loadConfig() {
  configPromise ??= api.get("/config").then((r) => r.data).catch(() => ({ channelId: null }));
  return configPromise;
}

const ICONS = {
  success: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><path d="m8 12 3 3 5-6"/></svg>',
  warning: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3 2 21h20L12 3z"/><path d="M12 10v4M12 18h.01"/></svg>',
  danger:  '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><path d="m15 9-6 6M9 9l6 6"/></svg>',
};

/** Escapa texto para inserir em HTML com segurança (evita XSS). */
function escapeHtml(text) {
  return String(text ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}

/** Notificação flutuante. `body` já deve estar escapado. */
function toast(type, title, body) {
  let box = document.querySelector(".toasts");
  if (!box) {
    box = document.createElement("div");
    box.className = "toasts";
    box.setAttribute("role", "status");
    box.setAttribute("aria-live", "polite");
    document.body.appendChild(box);
  }
  const el = document.createElement("div");
  el.className = `toast glass ${type}`;
  el.innerHTML = `<span class="ico">${ICONS[type]}</span><div class="body"><strong class="title">${escapeHtml(title)}</strong>${body}</div>`;
  box.appendChild(el);
  setTimeout(() => {
    el.classList.add("leaving");
    el.addEventListener("animationend", () => el.remove(), { once: true });
  }, 4500);
}

/** Liga/desliga o estado de carregamento de um botão. */
function setLoading(button, loading) {
  button.classList.toggle("loading", loading);
  button.disabled = loading;
  button.setAttribute("aria-busy", String(loading));
}
