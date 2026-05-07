# QuickBooks Importer

Internal Spring Boot + Vaadin app for importing invoice CSV files into QuickBooks Online.

## Stack

- Java 21+
- Spring Boot 4.0.5
- Vaadin 25.0.3
- PostgreSQL

## Local setup

1. Create a PostgreSQL database named `quickbooks_importer`.
2. Set these environment variables if you don't want the defaults:

```bash
export DB_URL=jdbc:postgresql://localhost:5432/quickbooks_importer
export DB_USERNAME=quickbooks
export DB_PASSWORD=quickbooks
export APP_USERNAME=admin
export APP_PASSWORD=admin123
export QB_CLIENT_ID=your_intuit_client_id
export QB_CLIENT_SECRET=your_intuit_client_secret
export QB_REDIRECT_URI=http://localhost:8080/oauth/quickbooks/callback
export QB_BASE_URL=https://sandbox-quickbooks.api.intuit.com
export QB_AUTH_URL=https://appcenter.intuit.com/connect/oauth2
export QB_TOKEN_URL=https://oauth.platform.intuit.com/oauth2/v1/tokens/bearer
export QB_ENVIRONMENT=sandbox
export QB_SERVICE_ITEM_INCOME_ACCOUNT_ID=your_income_account_id
```

3. Run the app:

```bash
mvn spring-boot:run
```

## App areas

- `/` invoice import
- `/settings` QuickBooks connection
- `/history` import history

## Notes

- The sample CSV reference is `resource/sample_invoice_import_tax. (1).csv`.
- v1 blocks direct import if any row is invalid.
- v1 exports a normalized QuickBooks-ready CSV even before QuickBooks import.
- Positive tax-rate invoice lines are flagged as unsupported for direct import in v1.
