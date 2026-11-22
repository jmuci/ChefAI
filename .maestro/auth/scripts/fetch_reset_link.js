// Reads the newest email from the backend's development-mode outbox (GET /dev/outbox, newest first)
// and publishes the reset link in the message as `output.resetLink`. Maestro runs on the host, so
// the backend is at localhost here, not 10.0.2.2 (that address is for the emulator).
const response = http.get('http://localhost:8080/dev/outbox')
const messages = json(response.body)
const body = messages.length > 0 ? messages[0].textBody : ''
const match = /https:\/\/pocket-chef\.app\/reset-password\?token=[A-Za-z0-9_-]{43}/.exec(body)
if (!match) {
  throw new Error('No reset link in the newest dev outbox message')
}
output.resetLink = match[0]
