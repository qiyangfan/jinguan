import js from "@eslint/js";
import jsxA11y from "eslint-plugin-jsx-a11y";
import simpleImportSort from "eslint-plugin-simple-import-sort";
import reactHooks from "eslint-plugin-react-hooks";
import reactRefresh from "eslint-plugin-react-refresh";
import tseslint from "typescript-eslint";
// 放在末尾关闭与 Prettier 冲突的格式规则。
import eslintConfigPrettier from "eslint-config-prettier";

export default tseslint.config(
  { ignores: ["dist", "coverage", "src/lib/api-types.gen.ts", "*.gen.ts"] },
  {
    // 源码开启类型感知:no-floating-promises / no-misused-promises 等依赖类型信息。
    extends: [
      js.configs.recommended,
      ...tseslint.configs.recommendedTypeChecked,
      jsxA11y.flatConfigs.recommended,
    ],
    files: ["src/**/*.{ts,tsx}"],
    languageOptions: {
      parserOptions: { projectService: true, tsconfigRootDir: import.meta.dirname },
    },
    plugins: {
      "react-hooks": reactHooks,
      "react-refresh": reactRefresh,
      "simple-import-sort": simpleImportSort,
    },
    rules: {
      ...reactHooks.configs.recommended.rules,
      "react-refresh/only-export-components": "off",
      "simple-import-sort/imports": "error",
      "simple-import-sort/exports": "error",
    },
  },
  {
    // 双仓标准件以 kongming 为源逐字节同步(宪法 Article 5):
    // 单边重排 import/export 会被 sync-frontend-standard.sh 覆盖,故只关排序,其余规则照常。
    files: [
      "src/components/ui/**",
      "src/components/{empty-state,error-alert,field-label,form-row,page-header,pagination,stat-card,status-badge}.tsx",
    ],
    rules: {
      "simple-import-sort/imports": "off",
      "simple-import-sort/exports": "off",
    },
  },
  {
    // 构建配置不在 tsconfig 工程内,只做语法级检查。
    extends: [js.configs.recommended, ...tseslint.configs.recommended],
    files: ["*.config.{ts,js}", "eslint.config.js"],
  },
  eslintConfigPrettier,
);
