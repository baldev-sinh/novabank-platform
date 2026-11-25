// @ts-check
// ESLint flat config, from `ng add angular-eslint` (https://github.com/angular-eslint/angular-eslint,
// docs/CONFIGURING_ESLINT.md). Formatting is Prettier's job, not ESLint's.
const eslint = require('@eslint/js');
const { defineConfig } = require('eslint/config');
const tseslint = require('typescript-eslint');
const angular = require('angular-eslint');

module.exports = defineConfig([
  {
    files: ['**/*.ts'],
    extends: [
      eslint.configs.recommended,
      tseslint.configs.recommended,
      tseslint.configs.stylistic,
      angular.configs.tsRecommended,
    ],
    // Lints inline templates with the HTML rules below. (The generated `processInlineTemplates`
    // is a deprecated alias of this.)
    processor: angular.createProcessor(),
    rules: {
      '@angular-eslint/directive-selector': [
        'error',
        { type: 'attribute', prefix: 'nb', style: 'camelCase' },
      ],
      '@angular-eslint/component-selector': [
        'error',
        { type: 'element', prefix: 'nb', style: 'kebab-case' },
      ],
      // `const { [key]: _removed, ...rest } = obj` is how an object loses one key.
      '@typescript-eslint/no-unused-vars': ['error', { ignoreRestSiblings: true }],
    },
  },
  {
    files: ['**/*.html'],
    extends: [angular.configs.templateRecommended, angular.configs.templateAccessibility],
    rules: {},
  },
]);
