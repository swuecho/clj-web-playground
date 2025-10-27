
## start cljs-repl

npx shadow-cljs cljs-repl :web

>(print "hello")
will print to console


##  Workflow Tips

- Reset state cleanly: define a restart/init! fn in your app and call it from the REPL instead of reloading the page.
- Capture useful snippets in dev namespaces; require them from the REPL to seed data, mock API responses, or mount alternate UIs on demand.