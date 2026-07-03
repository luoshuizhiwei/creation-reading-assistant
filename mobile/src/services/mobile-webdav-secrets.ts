import { deleteMobileSecret, readMobileSecret, writeMobileSecret } from "./mobile-secret-store";

const WEBDAV_PASSWORD_SECRET_ID = "mobile-webdav-password";

export async function loadWebDavPasswordSecret(): Promise<string | undefined> {
  return readMobileSecret(WEBDAV_PASSWORD_SECRET_ID);
}

export async function saveWebDavPasswordSecret(password: string): Promise<void> {
  const value = password.trim();
  if (!value) return;
  await writeMobileSecret(WEBDAV_PASSWORD_SECRET_ID, value);
}

export async function clearWebDavPasswordSecret(): Promise<void> {
  await deleteMobileSecret(WEBDAV_PASSWORD_SECRET_ID);
}

