import { useState } from "react";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { axe, toHaveNoViolations } from "jest-axe";
import * as api from "../src/api";
import Settings from "../src/pages/Settings";
import Garden from "../src/pages/Garden";
import Scanner from "../src/pages/Scanner";
import Chat from "../src/pages/Chat";
import Login from "../src/pages/Login";
import Plant from "../src/pages/Plant";
import { readAppearance } from "../src/appearance";

vi.mock("../src/api");
vi.mock("../src/image", () => ({ preparePhoto: async (file) => file }));
expect.extend(toHaveNoViolations);

const plant = { id: "plant-1", common_name: "Rosa", scientific_name: "Rosa chinensis", created_at: "2026-11-01", confidence: .8 };
const provider = { id: "provider-1", name: "Modelo propio", model: "demo", status: "activo",
  base_url: "https://example.invalid", key_suffix: "demo" };

function Preferences() {
  const [appearance, setAppearance] = useState(readAppearance);
  return <Settings username="Demo" appearance={appearance} onAppearance={setAppearance} />;
}

beforeEach(() => {
  vi.resetAllMocks();
  localStorage.clear();
  history.replaceState(null, "", "/chat");
  HTMLDialogElement.prototype.showModal = function () { this.open = true; };
  HTMLDialogElement.prototype.close = function () { this.open = false; };
  URL.createObjectURL = vi.fn(() => "blob:fixture");
  URL.revokeObjectURL = vi.fn();
  api.listIdentifiedPlants.mockResolvedValue({ plants: [plant] });
  api.listChatPlants.mockResolvedValue({ plant_ids: [plant.id] });
  api.getChatHistory.mockResolvedValue({ messages: [{ id: 1, role: "user", content: "Pregunta anterior" }] });
  api.getCapabilities.mockResolvedValue({ features: { chat_photo: { available: false, reason: "Modelo sin visión" } } });
  api.getPlantCoverUrl.mockResolvedValue(null);
  api.listProviders.mockResolvedValue({ providers: [provider], active_id: null });
  api.listPresets.mockResolvedValue({ presets: [] });
  api.getPlant.mockRejectedValue(new Error("Ficha no disponible"));
});

afterEach(cleanup);

test("settings retain appearance but clear the provider key immediately", async () => {
  render(<Preferences />);
  await screen.findByText("Modelo propio");
  fireEvent.change(screen.getByLabelText("Tema"), { target: { value: "dark" } });
  expect(readAppearance().mode).toBe("dark");
  fireEvent.click(screen.getByRole("button", { name: "Guardar apariencia" }));
  await screen.findByText("Apariencia sincronizada.");
  expect(api.savePreferences).toHaveBeenCalledWith(expect.objectContaining({ mode: "dark" }));

  for (const [label, value] of [["Nombre", "Prueba"], ["URL base", "https://example.invalid"],
    ["Identificador del modelo", "demo"], ["Clave del proveedor", "synthetic-key"]]) {
    fireEvent.change(screen.getByLabelText(label, { exact: true }), { target: { value } });
  }
  api.saveProvider.mockReturnValue(new Promise(() => {}));
  const key = screen.getByLabelText("Clave del proveedor");
  fireEvent.submit(key.form);
  expect(key.value).toBe("");
  expect(api.saveProvider).toHaveBeenCalledWith(expect.objectContaining({ api_key: "synthetic-key" }), undefined);
});

test("provider activation waits for explicit consent", async () => {
  render(<Preferences />);
  fireEvent.click(await screen.findByRole("button", { name: "Activar", exact: true }));
  expect(api.selectProvider).not.toHaveBeenCalled();
  const dialog = await screen.findByRole("dialog");
  fireEvent.click(within(dialog).getByRole("button", { name: "Acepto y activo" }));
  await screen.findByText("Proveedor activo.");
  expect(api.selectProvider).toHaveBeenCalledWith(provider.id, true);
});

test("chat disappears after confirmed deletion", async () => {
  render(<Chat />);
  await screen.findByText("Pregunta anterior");
  const menu = screen.getByLabelText("Opciones del chat");
  menu.parentElement.open = true;
  fireEvent.click(screen.getByRole("button", { name: "Eliminar conversación" }));
  expect(api.deleteChat).not.toHaveBeenCalled();
  fireEvent.click(within(await screen.findByRole("dialog")).getByRole("button", { name: "Eliminar" }));
  await screen.findByText("Aún no tienes conversaciones.");
  expect(api.deleteChat).toHaveBeenCalledWith(plant.id);
});

test("garden displays the server data", async () => {
  render(<Garden layout="list" />);
  expect(await screen.findByRole("link", { name: /Rosa chinensis/ })).toBeTruthy();
  expect(api.listIdentifiedPlants).toHaveBeenCalled();
});

test("scanner reports progress and aborts a pending identification", async () => {
  api.uploadImages.mockResolvedValue({ batch_id: "batch-1" });
  let signal;
  api.identify.mockImplementation((_batch, _organ, requestSignal) => {
    signal = requestSignal;
    return new Promise((_resolve, reject) => signal.addEventListener("abort",
      () => reject(new DOMException("Cancelled", "AbortError")), { once: true }));
  });
  const { container } = render(<Scanner onResult={vi.fn()} />);
  fireEvent.change(container.querySelector('input[type="file"]'), {
    target: { files: [new File(["image"], "rosa.jpg", { type: "image/jpeg" })] },
  });
  fireEvent.click(screen.getByRole("button", { name: "Identificar" }));
  await waitFor(() => expect(screen.getByRole("status").textContent).toMatch(/Analizando/));
  fireEvent.click(screen.getByRole("button", { name: "Cancelar espera" }));
  expect(signal.aborted).toBe(true);
  await screen.findByRole("button", { name: "Identificar" });
});

describe("accessibility smoke checks", () => {
  test.each([
    ["Settings", <Preferences />, () => screen.findByText("Modelo propio")],
    ["Chat", <Chat />, () => screen.findByText("Pregunta anterior")],
    ["Garden", <Garden />, () => screen.findByText("Rosa")],
    ["Scanner", <Scanner onResult={vi.fn()} />, null],
    ["Login", <Login onLogin={vi.fn()} />, null],
    ["Plant error state", <Plant id={plant.id} />, () => screen.findByRole("alert")],
    ["Plant detail", <Plant id={plant.id} />, () => screen.findByRole("heading", { name: "Rosa" }),
      () => api.getPlant.mockResolvedValue({ plant, summary: plant })],
  ])("%s", async (_name, page, ready, setup) => {
    setup?.();
    const { container } = render(<main>{page}</main>);
    await ready?.();
    expect(await axe(container)).toHaveNoViolations();
  });
});
