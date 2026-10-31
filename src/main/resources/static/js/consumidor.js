/* Consumidor: POST /api/consumidor */

const results = document.getElementById("results");
const lastRun = document.getElementById("last-run");
const buttons = document.querySelectorAll(".consume-option");

const STATUS = {
  ENVIADO: { cls: "success", label: "Enviado" },
  DEVOLVIDO: { cls: "warning", label: "Devolvido" },
  ERRO: { cls: "danger", label: "Erro" },
};

loadConfig().then(({ channelId }) => {
  document.querySelectorAll("[data-channel]").forEach((el) => (el.textContent = channelId || "canal não configurado"));
});

buttons.forEach((button) => button.addEventListener("click", () => consume(button)));

async function consume(button) {
  const canal = button.dataset.canal === "true";

  buttons.forEach((b) => (b.disabled = true));
  setLoading(button, true);
  results.innerHTML = '<div class="skeleton"></div><div class="skeleton"></div><div class="skeleton"></div>';
  try {
    const { data } = await api.post("/consumidor", { canal });
    render(data.resultados, canal);
  } catch (err) {
    results.innerHTML = emptyState("Não foi possível consumir", escapeHtml(errorMessage(err)));
    toast("danger", "Falha ao consumir", escapeHtml(errorMessage(err)));
  } finally {
    setLoading(button, false);
    buttons.forEach((b) => (b.disabled = false));
  }
}

function render(list, canal) {
  const count = (status) => list.filter((r) => r.status === status).length;
  document.getElementById("stat-total").textContent = list.length;
  document.getElementById("stat-enviado").textContent = count("ENVIADO");
  document.getElementById("stat-devolvido").textContent = count("DEVOLVIDO");
  document.getElementById("stat-erro").textContent = count("ERRO");
  lastRun.textContent = `${canal ? "Canal" : "Fila"} · ${new Date().toLocaleTimeString("pt-BR")}`;

  if (list.length === 0) {
    results.innerHTML = emptyState("Nenhuma mensagem disponível", `Não há mensagens ${canal ? "no canal" : "na fila"} no momento.`);
    toast("warning", "Fila vazia", `Nenhuma mensagem disponível ${canal ? "no canal" : "na fila"}.`);
    return;
  }

  const rows = list.map((r, i) => {
    const s = STATUS[r.status] || { cls: "", label: r.status };
    return `
      <tr style="animation-delay:${i * 40}ms">
        <td data-label="ID" class="mono">${escapeHtml(r.id)}</td>
        <td data-label="Canal">${r.channelId ? `<span class="chip channel plain">${escapeHtml(r.channelId)}</span>` : '<span class="muted">—</span>'}</td>
        <td data-label="Destinatário">${escapeHtml(r.destinatario)}</td>
        <td data-label="Entregas" class="mono">${escapeHtml(r.entregas)}</td>
        <td data-label="Status"><span class="chip ${s.cls}">${s.label}</span></td>
        <td data-label="Detalhe" class="detail">${escapeHtml(r.detalhe)}</td>
      </tr>`;
  }).join("");

  results.innerHTML = `
    <div class="table-wrap">
      <table>
        <thead><tr><th>ID</th><th>Canal</th><th>Destinatário</th><th>Entregas</th><th>Status</th><th>Detalhe</th></tr></thead>
        <tbody>${rows}</tbody>
      </table>
    </div>`;

  toast("success", "Lote processado",
    `${list.length} mensagem(ns): <strong>${count("ENVIADO")}</strong> enviada(s), <strong>${count("DEVOLVIDO")}</strong> devolvida(s).`);
}

function emptyState(title, text) {
  return `
    <div class="empty">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><path d="M8 15h8M9 9h.01M15 9h.01"/></svg>
      <strong>${escapeHtml(title)}</strong>
      <p>${text}</p>
    </div>`;
}
