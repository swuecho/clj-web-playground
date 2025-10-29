/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    "./public/index.html",
    "./src/**/*.{clj,cljs,cljc,js,jsx,ts,tsx}"
  ],
  theme: {
    extend: {}
  },
  daisyui: {
    logs: false,
    themes: [
      {
        kafka: {
          primary: "#4C4CFF", // brand 50
          "primary-content": "#F9FAFA",
          secondary: "#7E7EF1", // brand 30
          "secondary-content": "#0B0D0E",
          accent: "#33CC66", // green 50
          "accent-content": "#03160C",
          neutral: "#2F3639", // neutral 80
          "neutral-content": "#F9FAFA",
          "base-100": "#F9FAFA", // neutral 3
          "base-200": "#E3E6E8", // neutral 10
          "base-300": "#C7CED1", // neutral 20
          "base-content": "#171A1C", // neutral 90
          info: "#64B5F6", // blue 40
          "info-content": "#0B0D0E",
          success: "#33CC66",
          "success-content": "#0B0D0E",
          warning: "#FFDD57",
          "warning-content": "#171A1C",
          error: "#CF1717",
          "error-content": "#F9FAFA",
        },
      },
      {
        "kafka-dark": {
          primary: "#7E7EF1",
          "primary-content": "#F9FAFA",
          secondary: "#5B67E3",
          "secondary-content": "#F9FAFA",
          accent: "#5CD685",
          "accent-content": "#03160C",
          neutral: "#1E2224",
          "neutral-content": "#F1F2F3",
          "base-100": "#171A1C",
          "base-200": "#1E2224",
          "base-300": "#2F3639",
          "base-content": "#F1F2F3",
          info: "#64B5F6",
          "info-content": "#031A2B",
          success: "#33CC66",
          "success-content": "#0B160D",
          warning: "#FFDD57",
          "warning-content": "#171A1C",
          error: "#CF1717",
          "error-content": "#F9FAFA",
        },
      },
    ],
    darkTheme: "kafka-dark"
  },
  plugins: [require("daisyui")]
};
