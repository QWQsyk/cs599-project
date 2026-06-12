import http from 'node:http'
import fs from 'node:fs'
import path from 'node:path'

const root = path.dirname(new URL(import.meta.url).pathname).replace(/^\/([A-Za-z]:)/, '$1')

http.createServer((req, res) => {
  const file = path.join(root, 'index.html')
  res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' })
  res.end(fs.readFileSync(file))
}).listen(5173, () => console.log('Demo frontend listening on http://localhost:5173'))
