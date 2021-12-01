# fdk-dataset-preview-service

This service is responsible for providing a preview of a dataset distribution.

## Render as table
Currently this service only supports CSV (text/csv and application/vnd.ms-excel) 
to render as table.

## Env vars
`API_KEY` - API KEY used to access the endpoint

## Security
Add the `X-API-KEY` header to your requests and use the value configured for `API_KEY`.
