'use strict';
const { createApp } = require('./app');

const port = Number(process.env.PORT || 3000);
const { app } = createApp();
app.listen(port, () => console.log(`Savvy R&D backend listening on :${port}`));
process.on('SIGTERM', () => process.exit(0));
// Test harnesses start the backend with a stdin pipe: when the test process dies
// (even by crashing) the pipe closes and the backend exits instead of leaking.
if (process.env.SAVVY_EXIT_ON_STDIN_CLOSE === '1') {
  process.stdin.on('end', () => process.exit(0));
  process.stdin.resume();
}
