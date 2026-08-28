const { generateService } = require("@umijs/openapi");

const schemaPath =
  process.env.OPENAPI_SCHEMA_URL ||
  "https://api.example.com/api/v2/api-docs";

generateService({
  requestLibPath: "import request from '@/utils/request'",
  schemaPath,
  serversPath: "./src/api/generated"
});
