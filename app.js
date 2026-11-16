"use strict";

// The defaults mirror the mod, so the preview shows what players would see without the files.
const IMAGE_URL = "https://raw.githubusercontent.com/avie293/tabby-rpc/main/discord/";
const MENU_IMAGE = IMAGE_URL + "logo-7.png";
const WORLD_IMAGE = IMAGE_URL + "world.png";
const SERVER_ICON_URL = "https://api.mcstatus.io/v2/icon/";
const DOWNLOAD_BUTTON = {label: "Download Tabby RPC", url: "https://modrinth.com/mod/tabby-rpc"};
const SEPARATOR = " • ";
const MAX_TEXT = 128;
const MAX_IMAGE = 256;
const STORAGE_KEY = "tabby-rpc-builder";

const SAMPLE = {
  player: "Steve",
  loader: "Fabric",
  minecraft: "26.3",
  world: "New World",
  server: "play.example.com",
  players: "3",
  max_players: "20",
};
const PLACEHOLDERS = Object.keys(SAMPLE);

const SITUATIONS = [
  {key: "main_menu", title: "Main menu", details: "In the main menu"},
  {key: "options", title: "Options", details: "In the options"},
  {key: "multiplayer_menu", title: "Server list", details: "In the multiplayer menu"},
  {key: "singleplayer_menu", title: "World list", details: "In the singleplayer menu"},
  {key: "create_world", title: "Create world", details: "Creating a world"},
  {key: "menu", title: "Other menus", details: "In the menu"},
  {key: "singleplayer", title: "Singleplayer", details: "Singleplayer"},
  {key: "multiplayer", title: "Multiplayer", details: "Multiplayer"},
];
const FIELDS = [
  {key: "details", label: "First line"},
  {key: "state", label: "Second line"},
  {key: "large_image", label: "Large image", hint: "A URL, or the name of an art asset of your own application."},
  {key: "large_text", label: "Text when hovering the large image"},
];

const $ = (id) => document.getElementById(id);

let state = emptyState();
let current = SITUATIONS[0].key;
let lastField = null;
let application = {id: "", name: null, icon: null, assets: null, error: null};
let fetchTimer = 0;

function emptyState() {
  const situations = {};
  for (const situation of SITUATIONS) {
    situations[situation.key] = {details: "", state: "", large_image: "", large_text: ""};
  }
  return {
    applicationId: "",
    name: "",
    buttonMode: "default",
    buttons: [{label: "", url: ""}, {label: "", url: ""}],
    situations,
  };
}

function load() {
  try {
    const saved = JSON.parse(localStorage.getItem(STORAGE_KEY));
    if (saved && saved.situations) {
      const fresh = emptyState();
      state = {...fresh, ...saved, situations: {...fresh.situations, ...saved.situations}};
    }
  } catch (e) {
    // Private windows and blocked storage simply start empty.
  }
}

function save() {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
  } catch (e) {
    // Nothing to do, the page works without storage.
  }
}

// Same rules as the mod: a template that needs a hidden or unknown value falls back to the default as a whole.
function resolve(template, fallback, values) {
  if (!template) {
    return fallback;
  }
  let missing = false;
  const text = template.replace(/\{([^{}]*)\}/g, (match, name) => {
    if (!PLACEHOLDERS.includes(name)) {
      return match;
    }
    if (values[name] == null) {
      missing = true;
    }
    return values[name] ?? "";
  });
  return missing ? fallback : text;
}

function unknownPlaceholders(template) {
  return [...template.matchAll(/\{([^{}]*)\}/g)]
    .map((match) => match[1])
    .filter((name) => !PLACEHOLDERS.includes(name));
}

function previewOptions() {
  return {world: $("optWorld").checked, server: $("optServer").checked, join: $("optJoin").checked};
}

function situationValues(key, options) {
  const values = {player: SAMPLE.player, loader: SAMPLE.loader, minecraft: SAMPLE.minecraft};
  if (key === "singleplayer") {
    values.world = options.world ? SAMPLE.world : null;
    values.players = SAMPLE.players;
    values.max_players = SAMPLE.max_players;
  } else if (key === "multiplayer") {
    values.server = options.server ? SAMPLE.server : null;
    values.players = SAMPLE.players;
    values.max_players = SAMPLE.max_players;
  }
  return values;
}

function defaults(key, options) {
  const version = SAMPLE.loader + " " + SAMPLE.minecraft;
  const situation = SITUATIONS.find((s) => s.key === key);
  if (key === "singleplayer") {
    const stateText = options.world ? SAMPLE.world + SEPARATOR + version : version;
    return {details: situation.details, state: stateText, image: WORLD_IMAGE};
  }
  if (key === "multiplayer") {
    const players = SAMPLE.players + "/" + SAMPLE.max_players;
    return options.server
      ? {details: situation.details, state: SAMPLE.server + SEPARATOR + players, image: SERVER_ICON_URL + SAMPLE.server}
      : {details: situation.details, state: players, image: WORLD_IMAGE};
  }
  return {details: situation.details, state: version, image: MENU_IMAGE};
}

function isUrl(text) {
  return /^https?:\/\//i.test(text);
}

function assetUrl(name) {
  const asset = application.assets && application.assets.find((a) => a.name === name);
  return asset ? `https://cdn.discordapp.com/app-assets/${application.id}/${asset.id}.png` : null;
}

function customButtons() {
  return state.buttons.filter((b) => b.label.trim() && isUrl(b.url.trim()))
    .map((b) => ({label: b.label.trim(), url: b.url.trim()}));
}

/* Editor */

function renderTabs() {
  const tabs = $("tabs");
  tabs.replaceChildren();
  for (const situation of SITUATIONS) {
    const tab = document.createElement("button");
    tab.type = "button";
    tab.setAttribute("role", "tab");
    tab.textContent = situation.title;
    tab.setAttribute("aria-selected", situation.key === current);
    const values = state.situations[situation.key];
    tab.classList.toggle("tab--set", Object.values(values).some((v) => v.trim()));
    tab.addEventListener("click", () => {
      current = situation.key;
      renderTabs();
      renderSituation();
      update();
    });
    tabs.append(tab);
  }
}

function renderSituation() {
  const container = $("situation");
  container.replaceChildren();
  const options = previewOptions();
  const fallback = defaults(current, options);
  const image = current === "singleplayer" ? "world icon"
    : current === "multiplayer" ? "world icon, or the server icon while the address is shown" : "Tabby RPC logo";
  const shown = {details: fallback.details, state: fallback.state, large_image: image, large_text: "none"};

  for (const field of FIELDS) {
    const label = document.createElement("label");
    label.className = "field";
    const title = document.createElement("span");
    title.textContent = field.label;
    const input = document.createElement("input");
    input.value = state.situations[current][field.key];
    input.placeholder = "Default: " + shown[field.key];
    input.autocomplete = "off";
    input.dataset.field = field.key;
    input.addEventListener("input", () => {
      state.situations[current][field.key] = input.value;
      update();
      renderTabs();
    });
    input.addEventListener("focus", () => {
      lastField = input;
    });
    const warning = document.createElement("small");
    warning.className = "warning";
    warning.dataset.for = "situation." + field.key;
    label.append(title, input);
    if (field.hint) {
      const hint = document.createElement("small");
      hint.className = "hint";
      hint.textContent = field.hint;
      label.append(hint);
    }
    label.append(warning);
    container.append(label);
  }
  lastField = null;
}

function renderChips() {
  const chips = $("chips");
  for (const name of PLACEHOLDERS) {
    const chip = document.createElement("button");
    chip.type = "button";
    chip.className = "chip";
    chip.textContent = "{" + name + "}";
    // Keeps the focus in the field, so the placeholder lands at the cursor.
    chip.addEventListener("mousedown", (event) => event.preventDefault());
    chip.addEventListener("click", () => insert("{" + name + "}"));
    chips.append(chip);
  }
}

function insert(text) {
  const input = lastField || $("situation").querySelector("input");
  const start = input.selectionStart ?? input.value.length;
  const end = input.selectionEnd ?? input.value.length;
  input.setRangeText(text, start, end, "end");
  input.focus();
  input.dispatchEvent(new Event("input"));
}

/* Checks */

function warn(key, message) {
  const element = document.querySelector(`[data-for="${key}"]`);
  if (element) {
    element.textContent = message || "";
  }
}

function check() {
  const id = state.applicationId.trim();
  const status = $("applicationStatus");
  status.classList.remove("warning", "ok");
  if (!id) {
    status.textContent = "Leave empty to use Tabby RPC's application. With your own one, Discord shows its name "
      + "and icon, and art assets can be used by their name.";
  } else if (!/^\d+$/.test(id)) {
    status.textContent = "An application ID only has digits.";
    status.classList.add("warning");
  } else if (application.error) {
    status.textContent = application.error;
    status.classList.add("warning");
  } else if (application.name) {
    const count = application.assets ? application.assets.length : 0;
    status.textContent = `Found "${application.name}" with ${count} art asset${count === 1 ? "" : "s"}.`;
    status.classList.add("ok");
  } else {
    status.textContent = "Looking up the application...";
  }

  if (state.buttonMode === "custom") {
    const problems = [];
    state.buttons.forEach((button, index) => {
      const label = button.label.trim();
      const url = button.url.trim();
      if ((label || url) && !(label && url)) {
        problems.push(`Button ${index + 1} needs a label and a URL.`);
      } else if (url && !isUrl(url)) {
        problems.push(`The URL of button ${index + 1} has to start with https://.`);
      }
    });
    if (!problems.length && !customButtons().length) {
      problems.push("Without a filled in button, no button is shown at all.");
    }
    warn("buttons", problems.join(" "));
  }

  const values = state.situations[current];
  for (const field of FIELDS) {
    const text = values[field.key];
    const problems = [];
    const unknown = unknownPlaceholders(text);
    if (unknown.length) {
      problems.push("Unknown placeholder " + unknown.map((n) => "{" + n + "}").join(", ") + ".");
    }
    if (field.key === "large_image" && text.trim()) {
      if (text.length > MAX_IMAGE) {
        problems.push(`Discord only accepts up to ${MAX_IMAGE} characters, the default image is shown.`);
      } else if (!isUrl(text) && !text.includes("{")) {
        if (!id) {
          problems.push("Names of art assets only work with your own application.");
        } else if (application.assets && !assetUrl(text)) {
          problems.push("Your application has no art asset with this name.");
        }
      }
    } else if (text.length > MAX_TEXT) {
      problems.push(`Discord shows at most ${MAX_TEXT} characters, the rest is cut off.`);
    }
    warn("situation." + field.key, problems.join(" "));
  }
}

/* Preview */

function renderPreview() {
  const options = previewOptions();
  const fallback = defaults(current, options);
  const values = situationValues(current, options);
  const texts = state.situations[current];

  const id = state.applicationId.trim();
  const name = state.name.trim() || (id ? application.name || "Your application" : "Minecraft");
  $("previewName").textContent = name;
  $("previewDetails").textContent = clip(resolve(texts.details, fallback.details, values));
  let stateText = clip(resolve(texts.state, fallback.state, values));
  const joinable = options.join && current === "singleplayer";
  if (joinable) {
    stateText += ` (${SAMPLE.players} of ${SAMPLE.max_players})`;
  }
  $("previewState").textContent = stateText;

  let image = resolve(texts.large_image, fallback.image, values);
  if (image.length > MAX_IMAGE) {
    image = fallback.image;
  }
  const largeText = resolve(texts.large_text, "", values);
  const large = $("previewLarge");
  const key = $("previewLargeKey");
  const url = isUrl(image) ? image : assetUrl(image);
  large.hidden = !url;
  key.hidden = !!url;
  if (url) {
    large.src = url;
    large.title = largeText;
  } else {
    key.textContent = image;
    key.title = largeText;
  }

  const buttons = $("previewButtons");
  buttons.replaceChildren();
  let shownButtons = state.buttonMode === "custom" ? customButtons()
    : state.buttonMode === "none" ? [] : [DOWNLOAD_BUTTON];
  // Discord refuses buttons while others can ask to join.
  if (joinable) {
    shownButtons = [{label: "Ask to Join"}];
  }
  for (const button of shownButtons) {
    const element = document.createElement(button.url ? "a" : "span");
    element.className = "card__button";
    element.textContent = button.label;
    if (button.url) {
      element.href = button.url;
      element.target = "_blank";
      element.rel = "noopener";
    }
    buttons.append(element);
  }
}

function clip(text) {
  return text.length > MAX_TEXT ? text.slice(0, MAX_TEXT) : text;
}

/* Files */

function files() {
  const result = [];
  const settings = {};
  if (state.applicationId.trim()) {
    settings.application_id = state.applicationId.trim();
  }
  if (state.name.trim()) {
    settings.name = state.name.trim();
  }
  if (state.buttonMode === "custom") {
    settings.buttons = customButtons();
  } else if (state.buttonMode === "none") {
    settings.buttons = [];
  }
  if (Object.keys(settings).length) {
    result.push({name: "settings.json", json: settings});
  }

  for (const situation of SITUATIONS) {
    const json = {};
    for (const field of FIELDS) {
      const value = state.situations[situation.key][field.key];
      if (value.trim()) {
        json[field.key] = value;
      }
    }
    if (Object.keys(json).length) {
      result.push({name: situation.key + ".json", json});
    }
  }
  return result;
}

function renderFiles() {
  const list = $("files");
  list.replaceChildren();
  const all = files();
  if (!all.length) {
    const item = document.createElement("li");
    item.className = "hint";
    item.textContent = "Nothing changed yet, everything uses the defaults.";
    list.append(item);
  }
  for (const file of all) {
    const item = document.createElement("li");
    const code = document.createElement("code");
    code.textContent = "config/custom_rpc/" + file.name;
    item.append(code);
    list.append(item);
  }
  $("download").disabled = !all.length;
}

function download() {
  const entries = files().map((file) => ({
    path: "config/custom_rpc/" + file.name,
    text: JSON.stringify(file.json, null, 2) + "\n",
  }));
  const link = document.createElement("a");
  link.href = URL.createObjectURL(Zip.create(entries));
  link.download = "custom_rpc.zip";
  link.click();
  setTimeout(() => URL.revokeObjectURL(link.href), 1000);
}

/* Application lookup */

function lookUpApplication() {
  clearTimeout(fetchTimer);
  const id = state.applicationId.trim();
  application = {id, name: null, icon: null, assets: null, error: null};
  if (!/^\d+$/.test(id)) {
    return;
  }
  fetchTimer = setTimeout(async () => {
    try {
      const response = await fetch(`https://discord.com/api/v10/applications/${id}/rpc`);
      if (!response.ok) {
        throw new Error("unknown");
      }
      const data = await response.json();
      const assets = await fetch(`https://discord.com/api/v10/oauth2/applications/${id}/assets`)
        .then((r) => (r.ok ? r.json() : []))
        .catch(() => []);
      if (state.applicationId.trim() === id) {
        application = {id, name: data.name, icon: data.icon, assets, error: null};
      }
    } catch (e) {
      if (state.applicationId.trim() === id) {
        application.error = "Discord does not know this application.";
      }
    }
    update();
  }, 400);
}

/* Wiring */

function update() {
  check();
  renderPreview();
  renderFiles();
  save();
}

function fillGeneral() {
  $("applicationId").value = state.applicationId;
  $("name").value = state.name;
  document.querySelector(`input[name="buttonMode"][value="${state.buttonMode}"]`).checked = true;
  $("buttons").hidden = state.buttonMode !== "custom";
  for (const input of document.querySelectorAll("[data-button]")) {
    input.value = state.buttons[input.dataset.button][input.dataset.key];
  }
}

function init() {
  load();
  fillGeneral();
  renderTabs();
  renderSituation();
  renderChips();

  $("applicationId").addEventListener("input", (event) => {
    state.applicationId = event.target.value;
    lookUpApplication();
    update();
  });
  $("name").addEventListener("input", (event) => {
    state.name = event.target.value;
    update();
  });
  for (const radio of document.querySelectorAll('input[name="buttonMode"]')) {
    radio.addEventListener("change", () => {
      state.buttonMode = radio.value;
      $("buttons").hidden = state.buttonMode !== "custom";
      warn("buttons", "");
      update();
    });
  }
  for (const input of document.querySelectorAll("[data-button]")) {
    input.addEventListener("input", () => {
      state.buttons[input.dataset.button][input.dataset.key] = input.value;
      update();
    });
  }
  for (const id of ["optWorld", "optServer", "optJoin"]) {
    $(id).addEventListener("change", () => {
      renderSituation();
      update();
    });
  }
  $("download").addEventListener("click", download);
  $("reset").addEventListener("click", () => {
    if (confirm("Clear everything you have set up?")) {
      state = emptyState();
      application = {id: "", name: null, icon: null, assets: null, error: null};
      fillGeneral();
      renderTabs();
      renderSituation();
      update();
    }
  });

  lookUpApplication();
  update();
}

init();
