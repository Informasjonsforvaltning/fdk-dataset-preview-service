# fdk-dataset-preview-service

This service is responsible for providing a preview of a dataset distribution.

## Render as table
Currently this service only supports CSV (text/csv and application/vnd.ms-excel) 
to render as table.

## Env vars
`API_KEY` - API KEY used to access the endpoint
`ALLOWED_ORIGINS` - ALLOWED_ORIGINS used to configure cors settings

## Security
Add the `X-API-KEY` header to your requests and use the value configured for `API_KEY`.
Add the `X-XSRF-TOKEN` header to your requests and use the cookie value of `DATASET-PREVIEW-CSRF-TOKEN`.
