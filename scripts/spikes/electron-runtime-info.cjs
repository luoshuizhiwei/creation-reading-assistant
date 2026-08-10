const { app } = require("electron");

app.whenReady().then(() => {
  process.stdout.write(`${JSON.stringify(process.versions)}\n`);
  app.quit();
});

