#!/usr/bin/env node
const fs = require('fs');
function copyFromDefault(p) {
  if (!fs.existsSync(p)) {
    const defaultFile = `${p}.default`;
    if (fs.existsSync(defaultFile)) {
      fs.copyFileSync(defaultFile, p);
    }
  }
}
['.vscode/settings.json', '.vscode/tasks.json', '.vscode/launch.json'].map(copyFromDefault);
