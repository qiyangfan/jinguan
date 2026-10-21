/** @type {import('stylelint').Config} */
export default {
  extends: ["stylelint-config-standard"],
  rules: {
    // 允许 Tailwind 自定义 at-rule。
    "at-rule-no-unknown": [
      true,
      {
        ignoreAtRules: [
          "theme",
          "plugin",
          "custom-variant",
          "apply",
          "reference",
          "utility",
          "variant",
          "source",
          "config",
        ],
      },
    ],
    "import-notation": null, // Tailwind 使用 @import "tailwindcss"。
    // 跳过 Tailwind 指令参数校验。
    "at-rule-prelude-no-invalid": [
      true,
      { ignoreAtRules: ["apply", "theme", "utility", "variant"] },
    ],
  },
};
