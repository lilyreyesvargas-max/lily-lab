# ============================================================
# Clinical Management System - Makefile
# ============================================================
.PHONY: help init up down logs shell-odoo shell-db psql backup restore \
        load_demo load_demo_hr_inventory test \
        edi-generate-837 edi-send-837 edi-import-835 \
        edi-request-eligibility edi-process-eligibility edi-demo

COMPOSE = docker compose
ODOO_CONTAINER = clinic_odoo
DB_CONTAINER = clinic_db
ODOO_DB = odoo_clinic
ODOO_ADMIN = admin@clinic.local
ODOO_PASS = admin_local_strong
ODOO_URL = http://localhost:8070
SCRIPTS = ./scripts

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | \
	  awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-30s\033[0m %s\n", $$1, $$2}'

# ─── Infrastructure ───────────────────────────────────────────
init: ## First-time setup: start services and initialize Odoo DB with all clinic modules
	@echo ">>> Copying .env (if not present)..."
	@test -f .env || cp .env.example .env
	@echo ">>> Starting services..."
	$(COMPOSE) up -d
	@echo ">>> Waiting for PostgreSQL to be ready..."
	@until $(COMPOSE) exec -T db pg_isready -U odoo > /dev/null 2>&1; do \
	  echo "  ...waiting for db"; sleep 3; done
	@echo ">>> Initializing Odoo DB and installing all clinic modules..."
	$(COMPOSE) exec -T odoo odoo --config=/etc/odoo/odoo.conf \
	  --database=$(ODOO_DB) \
	  --init=clinic_core,clinic_patients,clinic_hr_payroll,clinic_ehr,\
clinic_appointments,clinic_insurance_billing,clinic_inventory_internal,\
clinic_edi_us,clinic_automation \
	  --without-demo=False \
	  --stop-after-init --no-http
	@echo ">>> Assigning clinic_group_admin to admin user..."
	$(COMPOSE) exec -T odoo python3 /mnt/extra-addons/../scripts/assign_admin_group.py \
	  --url http://localhost:8069 --db $(ODOO_DB) \
	  --user $(ODOO_ADMIN) --password $(ODOO_PASS) 2>/dev/null || true
	@echo ""
	@echo ">>> Init complete. Open $(ODOO_URL)"
	@echo ">>> Login: $(ODOO_ADMIN) / $(ODOO_PASS)"

up: ## Start all services
	$(COMPOSE) up -d

down: ## Stop all services
	$(COMPOSE) down

logs: ## Follow Odoo logs
	$(COMPOSE) logs -f odoo

shell-odoo: ## Bash shell inside Odoo container
	$(COMPOSE) exec odoo bash

shell-db: ## Bash shell inside DB container
	$(COMPOSE) exec db bash

psql: ## psql session on odoo_clinic DB
	$(COMPOSE) exec db psql -U odoo -d $(ODOO_DB)

backup: ## Backup database to ./backups/
	@mkdir -p backups
	$(COMPOSE) exec -T db pg_dump -U odoo $(ODOO_DB) | gzip > backups/$(ODOO_DB)_$$(date +%Y%m%d_%H%M%S).sql.gz
	@echo ">>> Backup saved to backups/"

restore: ## Restore latest backup (usage: make restore FILE=backups/xxx.sql.gz)
	@test -n "$(FILE)" || (echo "Usage: make restore FILE=backups/xxx.sql.gz" && exit 1)
	gunzip -c $(FILE) | $(COMPOSE) exec -T db psql -U odoo $(ODOO_DB)
	@echo ">>> Restore complete"

# ─── Demo Data ────────────────────────────────────────────────
load_demo_hr_inventory: ## Load HR, Inventory and Clinic Settings demo data
	@echo ">>> Loading HR, Inventory and Clinic Settings demo data..."
	python3 $(SCRIPTS)/load_demo_hr_inventory_settings.py \
	  --url $(ODOO_URL) --db $(ODOO_DB) \
	  --user $(ODOO_ADMIN) --password $(ODOO_PASS)
	@echo ">>> Done"

load_demo: ## Load all demo data (patients, doctors, claims, etc.)
	@echo ">>> Loading demo data..."
	$(COMPOSE) exec -T odoo python3 /edi/../mnt/extra-addons/../../../scripts/load_demo_data.py \
	  --url $(ODOO_URL) --db $(ODOO_DB) --user $(ODOO_ADMIN) --password $(ODOO_PASS) || \
	docker run --rm --network clinic_default \
	  -v $(PWD)/scripts:/scripts python:3.11-slim \
	  pip install xmlrpc > /dev/null 2>&1 && \
	  python /scripts/load_demo_data.py \
	  --url $(ODOO_URL) --db $(ODOO_DB) --user $(ODOO_ADMIN) --password $(ODOO_PASS)
	@echo ">>> Demo data loaded"

# ─── Tests ────────────────────────────────────────────────────
test: ## Run all Odoo module tests
	@echo ">>> Running clinic module tests..."
	$(COMPOSE) exec -T odoo odoo --config=/etc/odoo/odoo.conf \
	  --database=$(ODOO_DB) \
	  --test-enable \
	  --stop-after-init \
	  --log-level=test \
	  -i clinic_core,clinic_patients,clinic_ehr,clinic_appointments,\
clinic_insurance_billing,clinic_inventory_internal,clinic_hr_payroll,\
clinic_automation,clinic_edi_us

# ─── EDI Commands ─────────────────────────────────────────────
edi-generate-837: ## Generate 837P claim files from Odoo encounters
	@echo ">>> Generating 837P claims..."
	python3 $(SCRIPTS)/generate_837.py \
	  --url $(ODOO_URL) --db $(ODOO_DB) \
	  --user $(ODOO_ADMIN) --password $(ODOO_PASS) \
	  --output ./edi/out/837/
	@echo ">>> Claims written to ./edi/out/837/"

edi-send-837: ## Send 837 files to mock clearinghouse (REST)
	@echo ">>> Sending 837 to mock clearinghouse..."
	python3 $(SCRIPTS)/send_837.py \
	  --dir ./edi/out/837/ \
	  --url ${EDI_BASE_URL:-http://localhost:18080}
	@echo ">>> Done. Check ./edi/out/logs/"

edi-import-835: ## Import 835 remittance files from edi/in/835/
	@echo ">>> Importing 835 remittances..."
	python3 $(SCRIPTS)/import_835.py \
	  --url $(ODOO_URL) --db $(ODOO_DB) \
	  --user $(ODOO_ADMIN) --password $(ODOO_PASS) \
	  --dir ./edi/in/835/
	@echo ">>> Import complete"

edi-request-eligibility: ## Send 270 eligibility request to mock clearinghouse
	@echo ">>> Sending 270 eligibility request..."
	python3 $(SCRIPTS)/eligibility_270.py \
	  --url ${EDI_BASE_URL:-http://localhost:18080} \
	  --input ./edi/samples/sample_270_request.txt \
	  --output ./edi/out/270/
	@echo ">>> 270 sent"

edi-process-eligibility: ## Process 271 eligibility response into Odoo
	@echo ">>> Processing 271 eligibility responses..."
	python3 $(SCRIPTS)/process_271.py \
	  --url $(ODOO_URL) --db $(ODOO_DB) \
	  --user $(ODOO_ADMIN) --password $(ODOO_PASS) \
	  --dir ./edi/in/271/
	@echo ">>> Eligibility updated in Odoo"

edi-demo: ## Full EDI demo: generate → send → import → eligibility
	@echo "========================================="
	@echo "  EDI Full Demo Flow"
	@echo "========================================="
	@echo ""
	@echo "Step 1: Copying sample files..."
	cp edi/samples/sample_835_payment_full.txt edi/in/835/
	cp edi/samples/sample_271_response.txt edi/in/271/
	@echo ""
	@echo "Step 2: Generate 837 claims..."
	$(MAKE) edi-generate-837
	@echo ""
	@echo "Step 3: Send 837 to clearinghouse..."
	$(MAKE) edi-send-837
	@echo ""
	@echo "Step 4: Import 835 remittances..."
	$(MAKE) edi-import-835
	@echo ""
	@echo "Step 5: Request eligibility (270)..."
	$(MAKE) edi-request-eligibility
	@echo ""
	@echo "Step 6: Process eligibility (271)..."
	$(MAKE) edi-process-eligibility
	@echo ""
	@echo "========================================="
	@echo "  EDI Demo Complete!"
	@echo "  Check $(ODOO_URL)"
	@echo "========================================="
