# Included by nginx.conf. Strict script policy: no inline scripts (theme-init.js is a file), API calls
# only to this origin; if you point the UI at another API origin, add it to connect-src. Inline styles
# are allowed because Radix's scroll lock injects a <style> tag. vite.config.ts serves the same CSP
# for e2e (checked by src/test/csp.test.ts), and e2e fails on any CSP violation.
add_header Content-Security-Policy "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; connect-src 'self'; worker-src 'self'; object-src 'none'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'" always;
add_header X-Content-Type-Options "nosniff" always;
add_header Referrer-Policy "no-referrer" always;
add_header X-Frame-Options "DENY" always;
add_header Permissions-Policy "camera=(), microphone=(), geolocation=(), payment=()" always;
add_header Cross-Origin-Opener-Policy "same-origin" always;
