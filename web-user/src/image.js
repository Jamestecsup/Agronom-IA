export async function preparePhoto(file) {
  const bitmap = await createImageBitmap(file, { imageOrientation: "from-image" });
  try {
    const ratio = Math.min(1, 2048 / Math.max(bitmap.width, bitmap.height));
    const canvas = document.createElement("canvas");
    canvas.width = Math.max(1, Math.round(bitmap.width * ratio));
    canvas.height = Math.max(1, Math.round(bitmap.height * ratio));
    const context = canvas.getContext("2d", { alpha: false });
    context.fillStyle = "white";
    context.fillRect(0, 0, canvas.width, canvas.height);
    context.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
    const encoded = await new Promise((resolve) => canvas.toBlob(resolve, "image/jpeg", 0.85));
    if (!encoded) throw new Error("No pudimos convertir esta imagen.");
    return new File([encoded], file.name.replace(/\.[^.]+$/, "") + ".jpg", { type: "image/jpeg" });
  } finally {
    bitmap.close();
  }
}
