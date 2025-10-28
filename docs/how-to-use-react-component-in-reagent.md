# Using React Components (rc-component/table) in Reagent

This guide walks through wiring React components—specifically `@rc-component/table`—into a Reagent + Re-frame application. It covers project setup, interop patterns, and tips for managing props/styles so you can drop in other React libraries using the same approach.

## Set Up Dependencies

Add React and the package you want to use (e.g., rc-table) to `package.json`:
```json
{
  "dependencies": {
    "@rc-component/table": "^1.8.1",
    "react": "^18.3.1",
    "react-dom": "^18.3.1"
  }
}
```
Install packages (`npm install` or `pnpm install`). Make sure `deps.edn` declares Reagent ≥1.3.0 so you get `reagent.dom.client` for React 18.


## Import React Components in Reagent

Require components as you would in JavaScript, then render them with the `:>` hiccup form:

```clojure
(ns acme.web.views
  (:require
   [reagent.core :as r]
   ["@rc-component/table" :default rc-table]))

(defn users-table-rc []
  [:> rc-table {:columns columns
                :data data-js
                :rowKey "uuid"}])
```

`[:> component props children…]` creates a React element. Use `clj->js` to convert Clojure maps to plain JS objects when needed.

## Prepare Data and Props

rc-table expects specific columns/data shapes. You can keep your data in Clojure and convert only at the boundary:

```clojure
(let [columns (clj->js
               [{:title "UUID"
                 :dataIndex "uuid"
                 :key "uuid"
                 :render (fn [uuid _record _index]
                           (r/as-element [:span (or uuid "-")]))}
                {:title "Actions"
                 :dataIndex "uuid"
                 :key "actions"
                 :render (fn [uuid _record _index]
                           (r/as-element [user-actions uuid]))}])
      table-data (->> @users
                      (mapv (fn [user]
                              (assoc user :key (:uuid user))))
                      clj->js)]
  [:> rc-table {:columns columns
                :data table-data
                :rowKey "uuid"
                :tableLayout "auto"}])
```

- `r/as-element` turns Hiccup into an element when React asks for render functions.
- Use keywords or strings for `rowKey` (e.g., `"uuid"`).

## Styling React Components

React expects camelCase style keys, while Clojure maps often use kebab-case. Create a helper to convert them:

```clojure
(defn- camel-key [k]
  (let [parts (clojure.string/split (name k) #"-")]
    (apply str (first parts) (map clojure.string/capitalize (rest parts)))))

(defn- style->js [style-map]
  (->> style-map
       (map (fn [[k v]] [(camel-key k) v]))
       (into {})
       clj->js))

(let [header-style (style->js {:background "#f8fafc"
                               :font-weight 600
                               :border-bottom "1px solid #e2e8f0"})]
  {:onHeaderCell (fn [_] #js {:style header-style})})
```

## Handling Events

Inline functions (`(fn [event] ...)`) convert to JS functions automatically. Access DOM values as usual:

```clojure
[:input {:type "text"
         :value @name
         :on-change #(rf/dispatch [:update-name (.. % -target -value)])}]
```

For rc-table action columns you might pass Reagent components or dispatch events straight from the render function.

## Reusing Interop Helpers

Wrap patterns in utilities so you can reuse them for other React libraries:

- `style->js`: converts style maps.
- `->js-props`: converts nested Clojure data to JS equivalents with key transformation.
- `with-react` macros or helper functions to reduce repeated `clj->js` calls.

## Troubleshooting

- **Style warnings** (`Unsupported style property font-weight`): ensure camelCase keys (e.g. `fontWeight`).
- **React 18 warning (`ReactDOM.render is no longer supported`)**: switch to `reagent.dom.client/create-root` as shown earlier.
- **Unexpected props**: convert keys to strings (`:dataIndex "uuid"`) and ensure the JS object matches the React component’s API.
- **Empty table**: rc-table doesn’t render rows if `:rowKey` is missing. Make sure each row has a unique key.

## Applying to Other React Components

The same interop principles apply across React packages:

1. Require component (`["react-select" :default Select]`).
2. Prepare props as JS objects (`(clj->js {:value ...})`).
3. Render with `[:> Select props child]`.
4. Convert callbacks and nested data as needed.

## Summary Checklist

- ✅ React + component package installed via npm
- ✅ `:>` form with JS props (`clj->js`, camelCase styles)
- ✅ Render functions use `r/as-element` when returning Hiccup

### Optional: Styling with Sass

If you prefer SCSS, add `sass` as a dev dependency and compile to CSS. One convenient setup:

```json
"scripts": {
  "sass:watch": "sass --watch public/css/users-table-rc.scss:public/css/users-table-rc.css --no-source-map"
}
```

Run `npm run sass:watch` alongside Shadow-CLJS so the compiled stylesheet stays up to date. Point `public/index.html` at the generated `.css` file. Alternatively, you can register a Shadow-CLJS build hook (see `src/dev/dev/build_hooks.clj`) that shells out to `npx sass` whenever the build flushes; add the hook to your build’s `:build-hooks` vector in `shadow-cljs.edn` if you prefer a single watch process.

Once you’re comfortable with these steps, pulling in other React libraries becomes routine—you translate props to JS, leverage Reagent’s subscriptions and components for content, and keep styles/callbacks React-friendly.
