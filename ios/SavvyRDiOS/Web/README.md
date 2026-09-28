# Card domain hosting

Host `apple-app-site-association` at `https://<card-domain>/.well-known/apple-app-site-association`
(HTTPS, no redirects, `Content-Type: application/json`). Android needs
`https://<card-domain>/.well-known/assetlinks.json` (see android/ docs).

The same domain should serve a simple web page for `/c/*` for people without the
app installed: "This is a Savvy card. Install Savvy to use it." (iOS opens the
link in Safari when no app claims it.) Add a Smart App Banner so users who turned
universal links off for this domain can turn them back on with "Open".
