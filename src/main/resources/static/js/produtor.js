/* Produtor: POST /api/produtor */

const form = document.getElementById("form");
const email = document.getElementById("email");
const texto = document.getElementById("texto");
const counter = document.getElementById("counter");
const historyList = document.getElementById("history");
const empty = document.getElementById("empty");
const clearBtn = document.getElementById("clear");
const buttons = form.querySelectorAll("button[type=submit]");

const EMAIL_PATTERN = /^[^@\s]+@[^@\s]+$/;

loadConfig().then(({ channelId }) => {
  document.querySelectorAll("[data-channel]").forEach((el) => (el.textContent = channelId || "não configurado"));
});

// ---------- Validação ----------

function setInvalid(input, message) {
  const field = input.closest(".field");
  field.classList.toggle("invalid", Boolean(message));
  field.querySelector(".msg").textContent = message || "";
  input.setAttribute("aria-invalid", String(Boolean(message)));
}

function validate() {
  const emailMsg = !email.value.trim()
    ? "Informe o e-mail do destinatário."
    : !EMAIL_PATTERN.test(email.value.trim()) ? "Formato de e-mail inválido." : "";
  const textoMsg = !texto.value.trim() ? "O texto da mensagem não pode ficar vazio." : "";
  setInvalid(email, emailMsg);
  setInvalid(texto, textoMsg);
  if (emailMsg) email.focus();
  else if (textoMsg) texto.focus();
  return !emailMsg && !textoMsg;
}

email.addEventListener("input", () => email.closest(".field").classList.contains("invalid") && setInvalid(email, ""));
texto.addEventListener("input", () => {
  counter.textContent = `${texto.value.length} / 2000`;
  if (texto.closest(".field").classList.contains("invalid")) setInvalid(texto, "");
});

// ---------- Publicação ----------

form.addEventListener("submit", async (event) => {
  event.preventDefault();
  if (!validate()) return;

  const button = event.submitter || buttons[0];
  const canal = button.dataset.canal === "true";

  buttons.forEach((b) => (b.disabled = true));
  setLoading(button, true);
  try {
    const { data } = await api.post("/produtor", { email: email.value.trim(), texto: texto.value, canal });
    addToHistory(data, email.value.trim());
    toast("success", "Mensagem publicada",
      `ID <strong>${escapeHtml(data.messageId)}</strong> ${data.channelId ? `no canal <strong>${escapeHtml(data.channelId)}</strong>` : "na fila"}.`);
    form.reset();
    counter.textContent = "0 / 2000";
    email.focus();
  } catch (err) {
    const type = err.response && err.response.status === 400 ? "warning" : "danger";
    toast(type, type === "warning" ? "Verifique os dados" : "Falha ao publicar", escapeHtml(errorMessage(err)));
  } finally {
    setLoading(button, false);
    buttons.forEach((b) => (b.disabled = false));
  }
});

// ---------- Histórico ----------

function addToHistory({ messageId, channelId }, destinatario) {
  const li = document.createElement("li");
  const hora = new Date().toLocaleTimeString("pt-BR", { hour: "2-digit", minute: "2-digit", second: "2-digit" });
  li.innerHTML = `
    <span class="ico">${ICONS.success}</span>
    <div class="info">
      <strong>${escapeHtml(destinatario)}</strong>
      <span>ID ${escapeHtml(messageId)} · ${hora}</span>
    </div>
    ${channelId ? `<span class="chip channel">${escapeHtml(channelId)}</span>` : '<span class="chip">fila</span>'}`;
  historyList.prepend(li);
  empty.hidden = true;
  clearBtn.hidden = false;
}

clearBtn.addEventListener("click", () => {
  historyList.innerHTML = "";
  empty.hidden = false;
  clearBtn.hidden = true;
});
