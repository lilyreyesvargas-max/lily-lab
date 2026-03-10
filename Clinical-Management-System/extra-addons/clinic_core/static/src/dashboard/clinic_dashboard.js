/** @odoo-module **/
import { registry } from "@web/core/registry";
import { Component, useState, onWillStart } from "@odoo/owl";
import { useService } from "@web/core/utils/hooks";

export class ClinicDashboard extends Component {
    setup() {
        this.orm = useService("orm");
        this.action = useService("action");
        this.state = useState({
            patientCount: 0,
            appointmentCount: 0,
            claimCount: 0,
            pendingRemittances: 0,
        });
        onWillStart(async () => {
            await this.loadData();
        });
    }

    async loadData() {
        try { this.state.patientCount = await this.orm.searchCount("clinic.patient", []); } catch {}
        try { this.state.appointmentCount = await this.orm.searchCount("clinic.appointment", [["state", "=", "confirmed"]]); } catch {}
        try { this.state.claimCount = await this.orm.searchCount("clinic.billing.claim", [["state", "=", "draft"]]); } catch {}
        try { this.state.pendingRemittances = await this.orm.searchCount("clinic.remittance", [["state", "=", "pending"]]); } catch {}
    }

    openPatients() { this.action.doAction("clinic_patients.action_clinic_patient"); }
    openAppointments() { this.action.doAction("clinic_appointments.action_clinic_appointment"); }
    openClaims() { this.action.doAction("clinic_insurance_billing.action_clinic_billing_claim"); }
    openRemittances() { this.action.doAction("clinic_insurance_billing.action_clinic_remittance"); }
}

ClinicDashboard.template = "clinic_core.ClinicDashboard";
registry.category("actions").add("clinic_dashboard", ClinicDashboard);
