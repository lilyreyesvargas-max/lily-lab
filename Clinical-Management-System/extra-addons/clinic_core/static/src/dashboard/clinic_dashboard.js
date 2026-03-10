/** @odoo-module **/
import { registry } from "@web/core/registry";
import { Component, useState, onWillStart, onMounted, onWillUnmount, useRef } from "@odoo/owl";
import { useService } from "@web/core/utils/hooks";
import { loadJS } from "@web/core/assets";

// Bootstrap 5 palette used for charts
const COLORS = {
    primary:   "#0d6efd",
    info:      "#0dcaf0",
    success:   "#198754",
    warning:   "#ffc107",
    danger:    "#dc3545",
    secondary: "#6c757d",
    purple:    "#6f42c1",
    orange:    "#fd7e14",
};

// Helper: return today's date string "YYYY-MM-DD"
function todayStr() {
    return new Date().toISOString().slice(0, 10);
}

// Helper: return date string N days from today
function datePlusDays(n) {
    const d = new Date();
    d.setDate(d.getDate() + n);
    return d.toISOString().slice(0, 10);
}

// Helper: return Monday of current ISO week as Date
function weekStart() {
    const now = new Date();
    const day = now.getDay(); // 0=Sun
    const diff = (day === 0 ? -6 : 1 - day);
    const mon = new Date(now);
    mon.setDate(now.getDate() + diff);
    return mon;
}

// Helper: return last 7 day label strings ["Mon", ...]
function last7DayLabels() {
    const labels = [];
    const dayNames = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];
    for (let i = 6; i >= 0; i--) {
        const d = new Date();
        d.setDate(d.getDate() - i);
        labels.push(dayNames[d.getDay()] + " " + (d.getMonth() + 1) + "/" + d.getDate());
    }
    return labels;
}

// Helper: return next 7 day label strings
function next7DayLabels() {
    const labels = [];
    const dayNames = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];
    for (let i = 0; i < 7; i++) {
        const d = new Date();
        d.setDate(d.getDate() + i);
        labels.push(dayNames[d.getDay()] + " " + (d.getMonth() + 1) + "/" + d.getDate());
    }
    return labels;
}

export class ClinicDashboard extends Component {
    setup() {
        this.orm         = useService("orm");
        this.action      = useService("action");
        this.userService = useService("user");

        this.chartARef = useRef("chartA");
        this.chartBRef = useRef("chartB");
        this.chartCRef = useRef("chartC");
        this.chartDRef = useRef("chartD");

        this._chartA = null;
        this._chartB = null;
        this._chartC = null;
        this._chartD = null;

        this.state = useState({
            // Role flags
            role: "admin",      // admin | doctor | nurse | receptionist | billing
            roleLabel: "Administrator",

            // KPI values — each role uses its own subset
            kpi1: 0,
            kpi2: 0,
            kpi3: 0,
            kpi4: 0,
            kpi1Label: "",
            kpi2Label: "",
            kpi3Label: "",
            kpi4Label: "",
            kpi1Icon:  "fa-users",
            kpi2Icon:  "fa-calendar",
            kpi3Icon:  "fa-file-text",
            kpi4Icon:  "fa-dollar",
            kpi1Color: "primary",
            kpi2Color: "info",
            kpi3Color: "warning",
            kpi4Color: "success",
            kpi1Sub:   "",
            kpi2Sub:   "",
            kpi3Sub:   "",
            kpi4Sub:   "",

            // Chart titles and types
            chartATitle: "Appointments by State",
            chartBTitle: "Claims by State",
            chartCTitle: "",
            chartDTitle: "",

            chartAType:   "bar",
            chartBType:   "doughnut",
            chartCType:   "bar",
            chartDType:   "doughnut",

            // Chart data (set before onMounted)
            chartALabels: [],
            chartAData:   [],
            chartAColors: [],
            chartBLabels: [],
            chartBData:   [],
            chartBColors: [],
            chartCLabels: [],
            chartCData:   [],
            chartCColors: [],
            chartDLabels: [],
            chartDData:   [],
            chartDColors: [],
        });

        onWillStart(async () => {
            // Load Chart.js — temporarily disable AMD define so it sets window.Chart
            const savedDefine = window.define;
            window.define = undefined;
            await loadJS("/web/static/lib/Chart/Chart.js");
            window.define = savedDefine;
            await this._detectRole();
            await this._loadData();
        });

        onMounted(() => {
            this._renderCharts();
            // Force scroll on Odoo's parent containers
            let el = this.chartARef.el;
            while (el) {
                const style = window.getComputedStyle(el);
                if (style.overflow === "hidden" || style.overflowY === "hidden") {
                    el.style.overflowY = "auto";
                }
                el = el.parentElement;
                if (el && el.classList.contains("o_web_client")) break;
            }
        });

        onWillUnmount(() => {
            if (this._chartA) { this._chartA.destroy(); this._chartA = null; }
            if (this._chartB) { this._chartB.destroy(); this._chartB = null; }
            if (this._chartC) { this._chartC.destroy(); this._chartC = null; }
            if (this._chartD) { this._chartD.destroy(); this._chartD = null; }
        });
    }

    // ------------------------------------------------------------------ role detection

    async _detectRole() {
        try {
            const isAdmin         = await this.userService.hasGroup("clinic_core.clinic_group_admin");
            if (isAdmin) { this.state.role = "admin"; this.state.roleLabel = "Administrator"; return; }
            const isDoctor        = await this.userService.hasGroup("clinic_core.clinic_group_doctor");
            if (isDoctor) { this.state.role = "doctor"; this.state.roleLabel = "Doctor"; return; }
            const isNurse         = await this.userService.hasGroup("clinic_core.clinic_group_nurse");
            if (isNurse) { this.state.role = "nurse"; this.state.roleLabel = "Nurse"; return; }
            const isReceptionist  = await this.userService.hasGroup("clinic_core.clinic_group_receptionist");
            if (isReceptionist) { this.state.role = "receptionist"; this.state.roleLabel = "Receptionist"; return; }
            const isBilling       = await this.userService.hasGroup("clinic_core.clinic_group_billing");
            if (isBilling) { this.state.role = "billing"; this.state.roleLabel = "Billing"; return; }
            // fallback — superuser or unassigned user → show admin view
            this.state.role = "admin";
            this.state.roleLabel = "Administrator";
        } catch {
            this.state.role = "admin";
            this.state.roleLabel = "Administrator";
        }
    }

    // ------------------------------------------------------------------ data loading dispatcher

    async _loadData() {
        switch (this.state.role) {
            case "admin":        await this._loadAdmin(); break;
            case "doctor":       await this._loadDoctor(); break;
            case "nurse":        await this._loadNurse(); break;
            case "receptionist": await this._loadReceptionist(); break;
            case "billing":      await this._loadBilling(); break;
        }
    }

    // ------------------------------------------------------------------ admin

    async _loadAdmin() {
        const s = this.state;
        s.kpi1Label = "Total Patients";   s.kpi1Icon = "fa-users";     s.kpi1Color = "primary"; s.kpi1Sub = "Registered";
        s.kpi2Label = "Appointments";     s.kpi2Icon = "fa-calendar";  s.kpi2Color = "info";    s.kpi2Sub = "Confirmed";
        s.kpi3Label = "Draft Claims";     s.kpi3Icon = "fa-file-text"; s.kpi3Color = "warning"; s.kpi3Sub = "Pending review";
        s.kpi4Label = "Remittances";      s.kpi4Icon = "fa-dollar";    s.kpi4Color = "success"; s.kpi4Sub = "Pending ERA";

        try { s.kpi1 = await this.orm.searchCount("clinic.patient", []); } catch { s.kpi1 = 0; }
        try { s.kpi2 = await this.orm.searchCount("clinic.appointment", [["state", "=", "confirmed"]]); } catch { s.kpi2 = 0; }
        try { s.kpi3 = await this.orm.searchCount("clinic.billing.claim", [["state", "=", "draft"]]); } catch { s.kpi3 = 0; }
        try { s.kpi4 = await this.orm.searchCount("clinic.remittance", [["state", "=", "pending"]]); } catch { s.kpi4 = 0; }

        // Chart A: Appointments by state (bar)
        s.chartATitle = "Appointments by State";
        s.chartAType  = "bar";
        const apptStates = ["draft", "confirmed", "done", "cancelled"];
        const apptColors = [COLORS.secondary, COLORS.info, COLORS.success, COLORS.danger];
        try {
            const rows = await this.orm.readGroup("clinic.appointment", [], ["state"], ["state"]);
            const dataMap = {};
            rows.forEach(r => { dataMap[r.state] = r.state_count; });
            s.chartALabels = apptStates.map(st => st.charAt(0).toUpperCase() + st.slice(1));
            s.chartAData   = apptStates.map(st => dataMap[st] || 0);
            s.chartAColors = apptColors;
        } catch {
            s.chartALabels = apptStates.map(st => st.charAt(0).toUpperCase() + st.slice(1));
            s.chartAData   = [0, 0, 0, 0];
            s.chartAColors = apptColors;
        }

        // Chart B: Claims by state (doughnut)
        s.chartBTitle = "Claims by State";
        s.chartBType  = "doughnut";
        const claimStates  = ["draft", "submitted", "paid", "rejected"];
        const claimColors  = [COLORS.secondary, COLORS.info, COLORS.success, COLORS.danger];
        try {
            const rows = await this.orm.readGroup("clinic.billing.claim", [], ["state"], ["state"]);
            const dataMap = {};
            rows.forEach(r => { dataMap[r.state] = r.state_count; });
            s.chartBLabels = claimStates.map(st => st.charAt(0).toUpperCase() + st.slice(1));
            s.chartBData   = claimStates.map(st => dataMap[st] || 0);
            s.chartBColors = claimColors;
        } catch {
            s.chartBLabels = claimStates.map(st => st.charAt(0).toUpperCase() + st.slice(1));
            s.chartBData   = [0, 0, 0, 0];
            s.chartBColors = claimColors;
        }

        // Chart C: Remittances by status (doughnut)
        s.chartCTitle = "Remittances by Status";
        s.chartCType  = "doughnut";
        const remStates  = ["pending", "processed", "rejected"];
        const remColorsC = [COLORS.purple, COLORS.orange, COLORS.success];
        try {
            const rows = await this.orm.readGroup("clinic.remittance", [], ["state"], ["state"]);
            const dataMap = {};
            rows.forEach(r => { dataMap[r.state] = r.state_count; });
            s.chartCLabels = remStates.map(st => st.charAt(0).toUpperCase() + st.slice(1));
            s.chartCData   = remStates.map(st => dataMap[st] || 0);
            s.chartCColors = remColorsC;
        } catch {
            s.chartCLabels = remStates.map(st => st.charAt(0).toUpperCase() + st.slice(1));
            s.chartCData   = [0, 0, 0];
            s.chartCColors = remColorsC;
        }

        // Chart D: EDI Transactions by direction (bar)
        s.chartDTitle = "EDI Transactions by Direction";
        s.chartDType  = "bar";
        const ediDirections = ["inbound", "outbound"];
        const ediColorsD    = [COLORS.info, COLORS.warning];
        try {
            const rows = await this.orm.readGroup("clinic.edi.transaction", [], ["direction"], ["direction"]);
            const dataMap = {};
            rows.forEach(r => { dataMap[r.direction] = r.direction_count; });
            s.chartDLabels = ediDirections.map(d => d.charAt(0).toUpperCase() + d.slice(1));
            s.chartDData   = ediDirections.map(d => dataMap[d] || 0);
            s.chartDColors = ediColorsD;
        } catch {
            s.chartDLabels = ediDirections.map(d => d.charAt(0).toUpperCase() + d.slice(1));
            s.chartDData   = [0, 0];
            s.chartDColors = ediColorsD;
        }
    }

    // ------------------------------------------------------------------ doctor

    async _loadDoctor() {
        const s   = this.state;
        const uid = this.userService.userId;
        const today = todayStr();
        const weekEnd = datePlusDays(7);

        s.kpi1Label = "Today's Appts";    s.kpi1Icon = "fa-calendar-check-o"; s.kpi1Color = "primary"; s.kpi1Sub = "My schedule today";
        s.kpi2Label = "Pending Encounters"; s.kpi2Icon = "fa-stethoscope";    s.kpi2Color = "warning"; s.kpi2Sub = "Open encounters";
        s.kpi3Label = "Patients This Week"; s.kpi3Icon = "fa-users";          s.kpi3Color = "info";    s.kpi3Sub = "Unique this week";
        s.kpi4Label = "Low Stock Items";    s.kpi4Icon = "fa-exclamation-triangle"; s.kpi4Color = "danger"; s.kpi4Sub = "Below min qty";

        try {
            s.kpi1 = await this.orm.searchCount("clinic.appointment", [
                ["physician_id.user_id", "=", uid],
                ["date_appointment", ">=", today + " 00:00:00"],
                ["date_appointment", "<=", today + " 23:59:59"],
            ]);
        } catch { s.kpi1 = 0; }

        try {
            s.kpi2 = await this.orm.searchCount("clinic.ehr.encounter", [
                ["physician_id.user_id", "=", uid],
                ["state", "in", ["in_progress", "pending"]],
            ]);
        } catch { s.kpi2 = 0; }

        try {
            s.kpi3 = await this.orm.searchCount("clinic.appointment", [
                ["physician_id.user_id", "=", uid],
                ["date_appointment", ">=", weekStart().toISOString().slice(0, 10) + " 00:00:00"],
                ["date_appointment", "<=", weekEnd + " 23:59:59"],
            ]);
        } catch { s.kpi3 = 0; }

        try {
            s.kpi4 = await this.orm.searchCount("clinic.stock.item", [["qty_available", "<", 1]]);
        } catch { s.kpi4 = 0; }

        // Chart A: My appointments by day — last 7 days (bar)
        s.chartATitle  = "My Appointments (Last 7 Days)";
        s.chartAType   = "bar";
        const last7    = last7DayLabels();
        const last7Counts = new Array(7).fill(0);
        try {
            const startDate = datePlusDays(-6);
            const rows = await this.orm.searchRead(
                "clinic.appointment",
                [
                    ["physician_id.user_id", "=", uid],
                    ["date_appointment", ">=", startDate + " 00:00:00"],
                    ["date_appointment", "<=", today + " 23:59:59"],
                ],
                ["date_appointment"]
            );
            rows.forEach(r => {
                if (!r.date_appointment) return;
                const dStr = r.date_appointment.slice(0, 10);
                const dObj = new Date(dStr + "T00:00:00");
                const idx  = Math.round((dObj - new Date(startDate + "T00:00:00")) / 86400000);
                if (idx >= 0 && idx < 7) last7Counts[idx]++;
            });
        } catch { /* defaults to 0 */ }
        s.chartALabels = last7;
        s.chartAData   = last7Counts;
        s.chartAColors = new Array(7).fill(COLORS.primary);

        // Chart B: My appointments by state (doughnut)
        s.chartBTitle = "My Appointments by State";
        s.chartBType  = "doughnut";
        const apptStates  = ["draft", "confirmed", "done", "cancelled"];
        const apptColors  = [COLORS.secondary, COLORS.info, COLORS.success, COLORS.danger];
        try {
            const rows = await this.orm.readGroup(
                "clinic.appointment",
                [["physician_id.user_id", "=", uid]],
                ["state"], ["state"]
            );
            const dataMap = {};
            rows.forEach(r => { dataMap[r.state] = r.state_count; });
            s.chartBLabels = apptStates.map(st => st.charAt(0).toUpperCase() + st.slice(1));
            s.chartBData   = apptStates.map(st => dataMap[st] || 0);
            s.chartBColors = apptColors;
        } catch {
            s.chartBLabels = apptStates.map(st => st.charAt(0).toUpperCase() + st.slice(1));
            s.chartBData   = [0, 0, 0, 0];
            s.chartBColors = apptColors;
        }

        // Chart C: My encounters by state (bar)
        s.chartCTitle = "My Encounters by State";
        s.chartCType  = "bar";
        const encStates  = ["draft", "in_progress", "done", "cancelled"];
        const encColorsC = [COLORS.purple, COLORS.orange, COLORS.success, COLORS.danger];
        try {
            const rows = await this.orm.readGroup(
                "clinic.ehr.encounter",
                [["physician_id.user_id", "=", uid]],
                ["state"], ["state"]
            );
            const dataMap = {};
            rows.forEach(r => { dataMap[r.state] = r.state_count; });
            s.chartCLabels = encStates.map(st => st.replace(/_/g, " ").charAt(0).toUpperCase() + st.replace(/_/g, " ").slice(1));
            s.chartCData   = encStates.map(st => dataMap[st] || 0);
            s.chartCColors = encColorsC;
        } catch {
            s.chartCLabels = encStates.map(st => st.replace(/_/g, " ").charAt(0).toUpperCase() + st.replace(/_/g, " ").slice(1));
            s.chartCData   = [0, 0, 0, 0];
            s.chartCColors = encColorsC;
        }

        // Chart D: Patients by gender (doughnut)
        s.chartDTitle = "Patients by Gender";
        s.chartDType  = "doughnut";
        const genderColorsD = [COLORS.info, COLORS.warning, COLORS.secondary];
        try {
            const male   = await this.orm.searchCount("clinic.patient", [["gender", "=", "male"]]);
            const female = await this.orm.searchCount("clinic.patient", [["gender", "=", "female"]]);
            const other  = await this.orm.searchCount("clinic.patient", [["gender", "=", "other"]]);
            s.chartDLabels = ["Male", "Female", "Other"];
            s.chartDData   = [male, female, other];
            s.chartDColors = genderColorsD;
        } catch {
            s.chartDLabels = ["Male", "Female", "Other"];
            s.chartDData   = [0, 0, 0];
            s.chartDColors = genderColorsD;
        }
    }

    // ------------------------------------------------------------------ nurse

    async _loadNurse() {
        const s     = this.state;
        const today = todayStr();

        s.kpi1Label = "Active Encounters"; s.kpi1Icon = "fa-heartbeat";        s.kpi1Color = "success"; s.kpi1Sub = "In progress";
        s.kpi2Label = "Low Stock Alerts";  s.kpi2Icon = "fa-exclamation-triangle"; s.kpi2Color = "danger"; s.kpi2Sub = "Below minimum";
        s.kpi3Label = "Expiring Soon";     s.kpi3Icon = "fa-clock-o";          s.kpi3Color = "warning"; s.kpi3Sub = "Items expiring 30 days";
        s.kpi4Label = "Today's Schedule";  s.kpi4Icon = "fa-calendar";         s.kpi4Color = "info";    s.kpi4Sub = "Appointments today";

        try {
            s.kpi1 = await this.orm.searchCount("clinic.ehr.encounter", [["state", "=", "in_progress"]]);
        } catch { s.kpi1 = 0; }

        try {
            s.kpi2 = await this.orm.searchCount("stock.quant", [["quantity", "<=", 5]]);
        } catch { s.kpi2 = 0; }

        try {
            const expiryLimit = datePlusDays(30);
            s.kpi3 = await this.orm.searchCount("clinic.stock.item", [
                ["expiry_date", ">=", today],
                ["expiry_date", "<=", expiryLimit],
            ]);
        } catch { s.kpi3 = 0; }

        try {
            s.kpi4 = await this.orm.searchCount("clinic.appointment", [
                ["date_appointment", ">=", today + " 00:00:00"],
                ["date_appointment", "<=", today + " 23:59:59"],
            ]);
        } catch { s.kpi4 = 0; }

        // Chart A: Stock alerts by category (bar) — stock.quant qty <= 5, grouped by product categ
        s.chartATitle = "Stock Alerts by Category";
        s.chartAType  = "bar";
        try {
            const rows = await this.orm.readGroup(
                "stock.quant",
                [["quantity", "<=", 5]],
                ["product_id.categ_id"], ["product_id.categ_id"]
            );
            if (rows && rows.length > 0) {
                s.chartALabels = rows.map(r => (r["product_id.categ_id"] ? r["product_id.categ_id"][1] : "Uncategorized"));
                s.chartAData   = rows.map(r => r["product_id.categ_id_count"] || 0);
                s.chartAColors = rows.map((_, i) => Object.values(COLORS)[i % Object.values(COLORS).length]);
            } else {
                s.chartALabels = ["No Alerts"];
                s.chartAData   = [0];
                s.chartAColors = [COLORS.secondary];
            }
        } catch {
            s.chartALabels = ["No Data"];
            s.chartAData   = [0];
            s.chartAColors = [COLORS.secondary];
        }

        // Chart B: Encounters by state today (doughnut)
        s.chartBTitle = "Encounters by State Today";
        s.chartBType  = "doughnut";
        const encStates = ["scheduled", "in_progress", "done", "cancelled"];
        const encColors = [COLORS.secondary, COLORS.warning, COLORS.success, COLORS.danger];
        try {
            const rows = await this.orm.readGroup(
                "clinic.ehr.encounter",
                [
                    ["date_encounter", ">=", today + " 00:00:00"],
                    ["date_encounter", "<=", today + " 23:59:59"],
                ],
                ["state"], ["state"]
            );
            const dataMap = {};
            rows.forEach(r => { dataMap[r.state] = r.state_count; });
            s.chartBLabels = encStates.map(st => st.replace(/_/g, " ").charAt(0).toUpperCase() + st.replace(/_/g, " ").slice(1));
            s.chartBData   = encStates.map(st => dataMap[st] || 0);
            s.chartBColors = encColors;
        } catch {
            s.chartBLabels = encStates.map(st => st.replace(/_/g, " ").charAt(0).toUpperCase() + st.replace(/_/g, " ").slice(1));
            s.chartBData   = [0, 0, 0, 0];
            s.chartBColors = encColors;
        }

        // Chart C: Supply requests by state (bar)
        s.chartCTitle = "Supply Requests by State";
        s.chartCType  = "bar";
        const supplyStates  = ["draft", "confirmed", "received", "cancelled"];
        const supplyColorsC = [COLORS.purple, COLORS.orange, COLORS.success, COLORS.danger];
        try {
            const rows = await this.orm.readGroup("clinic.supply.request", [], ["state"], ["state"]);
            const dataMap = {};
            rows.forEach(r => { dataMap[r.state] = r.state_count; });
            s.chartCLabels = supplyStates.map(st => st.charAt(0).toUpperCase() + st.slice(1));
            s.chartCData   = supplyStates.map(st => dataMap[st] || 0);
            s.chartCColors = supplyColorsC;
        } catch {
            s.chartCLabels = supplyStates.map(st => st.charAt(0).toUpperCase() + st.slice(1));
            s.chartCData   = [0, 0, 0, 0];
            s.chartCColors = supplyColorsC;
        }

        // Chart D: Appointments today by state (doughnut)
        s.chartDTitle = "Appointments Today by State";
        s.chartDType  = "doughnut";
        const apptStates  = ["draft", "confirmed", "done", "cancelled"];
        const apptColorsD = [COLORS.info, COLORS.warning, COLORS.secondary, COLORS.primary];
        try {
            const rows = await this.orm.readGroup(
                "clinic.appointment",
                [
                    ["date_appointment", ">=", today + " 00:00:00"],
                    ["date_appointment", "<=", today + " 23:59:59"],
                ],
                ["state"], ["state"]
            );
            const dataMap = {};
            rows.forEach(r => { dataMap[r.state] = r.state_count; });
            s.chartDLabels = apptStates.map(st => st.charAt(0).toUpperCase() + st.slice(1));
            s.chartDData   = apptStates.map(st => dataMap[st] || 0);
            s.chartDColors = apptColorsD;
        } catch {
            s.chartDLabels = apptStates.map(st => st.charAt(0).toUpperCase() + st.slice(1));
            s.chartDData   = [0, 0, 0, 0];
            s.chartDColors = apptColorsD;
        }
    }

    // ------------------------------------------------------------------ receptionist

    async _loadReceptionist() {
        const s     = this.state;
        const today = todayStr();
        const next7 = datePlusDays(7);

        s.kpi1Label = "Today's Appts";    s.kpi1Icon = "fa-calendar"; s.kpi1Color = "primary"; s.kpi1Sub = "Scheduled today";
        s.kpi2Label = "Waiting";          s.kpi2Icon = "fa-hourglass-half"; s.kpi2Color = "warning"; s.kpi2Sub = "Patients waiting";
        s.kpi3Label = "This Week's Appts"; s.kpi3Icon = "fa-calendar-o"; s.kpi3Color = "info"; s.kpi3Sub = "Next 7 days";
        s.kpi4Label = "New Patients";     s.kpi4Icon = "fa-user-plus"; s.kpi4Color = "success"; s.kpi4Sub = "Registered today";

        try {
            s.kpi1 = await this.orm.searchCount("clinic.appointment", [
                ["date_appointment", ">=", today + " 00:00:00"],
                ["date_appointment", "<=", today + " 23:59:59"],
            ]);
        } catch { s.kpi1 = 0; }

        try {
            s.kpi2 = await this.orm.searchCount("clinic.appointment", [
                ["state", "=", "confirmed"],
                ["date_appointment", ">=", today + " 00:00:00"],
                ["date_appointment", "<=", today + " 23:59:59"],
            ]);
        } catch { s.kpi2 = 0; }

        try {
            s.kpi3 = await this.orm.searchCount("clinic.appointment", [
                ["date_appointment", ">=", today + " 00:00:00"],
                ["date_appointment", "<=", next7 + " 23:59:59"],
            ]);
        } catch { s.kpi3 = 0; }

        try {
            s.kpi4 = await this.orm.searchCount("clinic.patient", [
                ["create_date", ">=", today + " 00:00:00"],
                ["create_date", "<=", today + " 23:59:59"],
            ]);
        } catch { s.kpi4 = 0; }

        // Chart A: Appointments next 7 days per day (bar)
        s.chartATitle = "Appointments — Next 7 Days";
        s.chartAType  = "bar";
        const next7Labels  = next7DayLabels();
        const next7Counts  = new Array(7).fill(0);
        try {
            const rows = await this.orm.searchRead(
                "clinic.appointment",
                [
                    ["date_appointment", ">=", today + " 00:00:00"],
                    ["date_appointment", "<=", next7 + " 23:59:59"],
                ],
                ["date_appointment"]
            );
            rows.forEach(r => {
                if (!r.date_appointment) return;
                const dStr = r.date_appointment.slice(0, 10);
                const dObj = new Date(dStr + "T00:00:00");
                const idx  = Math.round((dObj - new Date(today + "T00:00:00")) / 86400000);
                if (idx >= 0 && idx < 7) next7Counts[idx]++;
            });
        } catch { /* defaults to 0 */ }
        s.chartALabels = next7Labels;
        s.chartAData   = next7Counts;
        s.chartAColors = new Array(7).fill(COLORS.info);

        // Chart B: Appointments by state today (doughnut)
        s.chartBTitle = "Today's Appointments by State";
        s.chartBType  = "doughnut";
        const apptStates = ["draft", "confirmed", "done", "cancelled"];
        const apptColors = [COLORS.secondary, COLORS.info, COLORS.success, COLORS.danger];
        try {
            const rows = await this.orm.readGroup(
                "clinic.appointment",
                [
                    ["date_appointment", ">=", today + " 00:00:00"],
                    ["date_appointment", "<=", today + " 23:59:59"],
                ],
                ["state"], ["state"]
            );
            const dataMap = {};
            rows.forEach(r => { dataMap[r.state] = r.state_count; });
            s.chartBLabels = apptStates.map(st => st.charAt(0).toUpperCase() + st.slice(1));
            s.chartBData   = apptStates.map(st => dataMap[st] || 0);
            s.chartBColors = apptColors;
        } catch {
            s.chartBLabels = apptStates.map(st => st.charAt(0).toUpperCase() + st.slice(1));
            s.chartBData   = [0, 0, 0, 0];
            s.chartBColors = apptColors;
        }

        // Chart C: New patients this week — per day (bar)
        s.chartCTitle = "New Patients This Week";
        s.chartCType  = "bar";
        const week7Labels = last7DayLabels();
        const week7Counts = new Array(7).fill(0);
        const weekStartDate = datePlusDays(-6);
        try {
            const rows = await this.orm.searchRead(
                "clinic.patient",
                [
                    ["create_date", ">=", weekStartDate + " 00:00:00"],
                    ["create_date", "<=", today + " 23:59:59"],
                ],
                ["create_date"]
            );
            rows.forEach(r => {
                if (!r.create_date) return;
                const dStr = r.create_date.slice(0, 10);
                const dObj = new Date(dStr + "T00:00:00");
                const idx  = Math.round((dObj - new Date(weekStartDate + "T00:00:00")) / 86400000);
                if (idx >= 0 && idx < 7) week7Counts[idx]++;
            });
        } catch { /* defaults to 0 */ }
        s.chartCLabels = week7Labels;
        s.chartCData   = week7Counts;
        s.chartCColors = new Array(7).fill(COLORS.purple);

        // Chart D: Appointments by physician today (bar)
        s.chartDTitle = "Appointments by Physician Today";
        s.chartDType  = "bar";
        try {
            const rows = await this.orm.readGroup(
                "clinic.appointment",
                [
                    ["date_appointment", ">=", today + " 00:00:00"],
                    ["date_appointment", "<=", today + " 23:59:59"],
                ],
                ["physician_id"], ["physician_id"]
            );
            if (rows && rows.length > 0) {
                s.chartDLabels = rows.map(r => (r.physician_id ? r.physician_id[1] : "Unassigned"));
                s.chartDData   = rows.map(r => r.physician_id_count || 0);
                s.chartDColors = rows.map((_, i) => [COLORS.info, COLORS.warning, COLORS.secondary, COLORS.primary][i % 4]);
            } else {
                s.chartDLabels = ["No Appointments"];
                s.chartDData   = [0];
                s.chartDColors = [COLORS.info];
            }
        } catch {
            s.chartDLabels = ["No Data"];
            s.chartDData   = [0];
            s.chartDColors = [COLORS.info];
        }
    }

    // ------------------------------------------------------------------ billing

    async _loadBilling() {
        const s     = this.state;
        const today = todayStr();
        const monthStart = today.slice(0, 7) + "-01";

        s.kpi1Label = "Draft Claims";     s.kpi1Icon = "fa-file-text";    s.kpi1Color = "secondary"; s.kpi1Sub = "Awaiting submission";
        s.kpi2Label = "Submitted Claims"; s.kpi2Icon = "fa-paper-plane";  s.kpi2Color = "info";      s.kpi2Sub = "Sent to payer";
        s.kpi3Label = "Paid This Month";  s.kpi3Icon = "fa-check-circle"; s.kpi3Color = "success";   s.kpi3Sub = "Claims paid";
        s.kpi4Label = "Rejected Claims";  s.kpi4Icon = "fa-times-circle"; s.kpi4Color = "danger";    s.kpi4Sub = "Needs correction";

        try { s.kpi1 = await this.orm.searchCount("clinic.billing.claim", [["state", "=", "draft"]]); } catch { s.kpi1 = 0; }
        try { s.kpi2 = await this.orm.searchCount("clinic.billing.claim", [["state", "=", "submitted"]]); } catch { s.kpi2 = 0; }
        try {
            s.kpi3 = await this.orm.searchCount("clinic.billing.claim", [
                ["state", "=", "paid"],
                ["date_paid", ">=", monthStart],
                ["date_paid", "<=", today],
            ]);
        } catch { s.kpi3 = 0; }
        try { s.kpi4 = await this.orm.searchCount("clinic.billing.claim", [["state", "=", "rejected"]]); } catch { s.kpi4 = 0; }

        // Chart A: Claims by state (doughnut)
        s.chartATitle = "Claims by State";
        s.chartAType  = "doughnut";
        const claimStates = ["draft", "submitted", "paid", "rejected"];
        const claimColors = [COLORS.secondary, COLORS.info, COLORS.success, COLORS.danger];
        try {
            const rows = await this.orm.readGroup("clinic.billing.claim", [], ["state"], ["state"]);
            const dataMap = {};
            rows.forEach(r => { dataMap[r.state] = r.state_count; });
            s.chartALabels = claimStates.map(st => st.charAt(0).toUpperCase() + st.slice(1));
            s.chartAData   = claimStates.map(st => dataMap[st] || 0);
            s.chartAColors = claimColors;
        } catch {
            s.chartALabels = claimStates.map(st => st.charAt(0).toUpperCase() + st.slice(1));
            s.chartAData   = [0, 0, 0, 0];
            s.chartAColors = claimColors;
        }

        // Chart B: Remittances by status (doughnut)
        s.chartBTitle = "Remittances by Status";
        s.chartBType  = "doughnut";
        const remStates = ["pending", "posted", "reconciled", "cancelled"];
        const remColors = [COLORS.warning, COLORS.info, COLORS.success, COLORS.danger];
        try {
            const rows = await this.orm.readGroup("clinic.remittance", [], ["state"], ["state"]);
            const dataMap = {};
            rows.forEach(r => { dataMap[r.state] = r.state_count; });
            s.chartBLabels = remStates.map(st => st.charAt(0).toUpperCase() + st.slice(1));
            s.chartBData   = remStates.map(st => dataMap[st] || 0);
            s.chartBColors = remColors;
        } catch {
            s.chartBLabels = remStates.map(st => st.charAt(0).toUpperCase() + st.slice(1));
            s.chartBData   = [0, 0, 0, 0];
            s.chartBColors = remColors;
        }

        // Chart C: EDI transactions by state (bar)
        s.chartCTitle = "EDI Transactions by State";
        s.chartCType  = "bar";
        const ediStates  = ["draft", "sent", "acknowledged", "rejected"];
        const ediColorsC = [COLORS.purple, COLORS.orange, COLORS.success, COLORS.danger];
        try {
            const rows = await this.orm.readGroup("clinic.edi.transaction", [], ["state"], ["state"]);
            const dataMap = {};
            rows.forEach(r => { dataMap[r.state] = r.state_count; });
            s.chartCLabels = ediStates.map(st => st.charAt(0).toUpperCase() + st.slice(1));
            s.chartCData   = ediStates.map(st => dataMap[st] || 0);
            s.chartCColors = ediColorsC;
        } catch {
            s.chartCLabels = ediStates.map(st => st.charAt(0).toUpperCase() + st.slice(1));
            s.chartCData   = [0, 0, 0, 0];
            s.chartCColors = ediColorsC;
        }

        // Chart D: Claims by insurance provider (bar)
        s.chartDTitle = "Claims by Insurance Provider";
        s.chartDType  = "bar";
        try {
            const rows = await this.orm.readGroup("clinic.billing.claim", [], ["insurer_id"], ["insurer_id"]);
            if (rows && rows.length > 0) {
                s.chartDLabels = rows.map(r => (r.insurer_id ? r.insurer_id[1] : "No Insurer"));
                s.chartDData   = rows.map(r => r.insurer_id_count || 0);
                s.chartDColors = rows.map((_, i) => [COLORS.info, COLORS.warning, COLORS.secondary, COLORS.primary][i % 4]);
            } else {
                s.chartDLabels = ["No Data"];
                s.chartDData   = [0];
                s.chartDColors = [COLORS.info];
            }
        } catch {
            s.chartDLabels = ["No Data"];
            s.chartDData   = [0];
            s.chartDColors = [COLORS.info];
        }
    }

    // ------------------------------------------------------------------ chart rendering

    _renderCharts() {
        if (!window.Chart) return;

        // Destroy previous instances (safe re-render)
        if (this._chartA) { this._chartA.destroy(); this._chartA = null; }
        if (this._chartB) { this._chartB.destroy(); this._chartB = null; }
        if (this._chartC) { this._chartC.destroy(); this._chartC = null; }
        if (this._chartD) { this._chartD.destroy(); this._chartD = null; }

        const s = this.state;

        if (this.chartARef.el) {
            this._chartA = new window.Chart(this.chartARef.el, {
                type: s.chartAType,
                data: {
                    labels: s.chartALabels,
                    datasets: [{
                        label: s.chartATitle,
                        data:  s.chartAData,
                        backgroundColor: s.chartAColors,
                        borderColor:     s.chartAColors,
                        borderWidth: 1,
                    }],
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    plugins: {
                        legend: { display: s.chartAType === "doughnut" },
                    },
                    scales: s.chartAType === "bar"
                        ? { y: { beginAtZero: true, ticks: { stepSize: 1 } } }
                        : {},
                },
            });
        }

        if (this.chartBRef.el) {
            this._chartB = new window.Chart(this.chartBRef.el, {
                type: s.chartBType,
                data: {
                    labels: s.chartBLabels,
                    datasets: [{
                        label: s.chartBTitle,
                        data:  s.chartBData,
                        backgroundColor: s.chartBColors,
                        borderColor:     s.chartBColors,
                        borderWidth: 1,
                    }],
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    plugins: {
                        legend: { display: true },
                    },
                    scales: s.chartBType === "bar"
                        ? { y: { beginAtZero: true, ticks: { stepSize: 1 } } }
                        : {},
                },
            });
        }

        if (this.chartCRef.el) {
            this._chartC = new window.Chart(this.chartCRef.el, {
                type: s.chartCType,
                data: {
                    labels: s.chartCLabels,
                    datasets: [{
                        label: s.chartCTitle,
                        data:  s.chartCData,
                        backgroundColor: s.chartCColors,
                        borderColor:     s.chartCColors,
                        borderWidth: 1,
                    }],
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    plugins: {
                        legend: { display: s.chartCType === "doughnut" },
                    },
                    scales: s.chartCType === "bar"
                        ? { y: { beginAtZero: true, ticks: { stepSize: 1 } } }
                        : {},
                },
            });
        }

        if (this.chartDRef.el) {
            this._chartD = new window.Chart(this.chartDRef.el, {
                type: s.chartDType,
                data: {
                    labels: s.chartDLabels,
                    datasets: [{
                        label: s.chartDTitle,
                        data:  s.chartDData,
                        backgroundColor: s.chartDColors,
                        borderColor:     s.chartDColors,
                        borderWidth: 1,
                    }],
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    plugins: {
                        legend: { display: true },
                    },
                    scales: s.chartDType === "bar"
                        ? { y: { beginAtZero: true, ticks: { stepSize: 1 } } }
                        : {},
                },
            });
        }
    }

    // ------------------------------------------------------------------ navigation helpers

    openPatients()      { try { this.action.doAction("clinic_patients.action_clinic_patient"); }       catch {} }
    openAppointments()  { try { this.action.doAction("clinic_appointments.action_clinic_appointment"); } catch {} }
    openClaims()        { try { this.action.doAction("clinic_insurance_billing.action_clinic_billing_claim"); } catch {} }
    openRemittances()   { try { this.action.doAction("clinic_insurance_billing.action_clinic_remittance"); }   catch {} }
    openEncounters()    { try { this.action.doAction("clinic_encounters.action_clinic_encounter"); }   catch {} }
    openStock()         { try { this.action.doAction("clinic_inventory.action_clinic_stock_item"); }   catch {} }

    // ------------------------------------------------------------------ KPI click dispatchers
    // Each KPI slot maps to the most relevant action for the current role.

    _kpi1Click() {
        switch (this.state.role) {
            case "admin":        this.openPatients();     break;
            case "doctor":       this.openAppointments(); break;
            case "nurse":        this.openEncounters();   break;
            case "receptionist": this.openAppointments(); break;
            case "billing":      this.openClaims();       break;
        }
    }

    _kpi2Click() {
        switch (this.state.role) {
            case "admin":        this.openAppointments(); break;
            case "doctor":       this.openEncounters();   break;
            case "nurse":        this.openStock();        break;
            case "receptionist": this.openAppointments(); break;
            case "billing":      this.openClaims();       break;
        }
    }

    _kpi3Click() {
        switch (this.state.role) {
            case "admin":        this.openClaims();       break;
            case "doctor":       this.openAppointments(); break;
            case "nurse":        this.openStock();        break;
            case "receptionist": this.openAppointments(); break;
            case "billing":      this.openClaims();       break;
        }
    }

    _kpi4Click() {
        switch (this.state.role) {
            case "admin":        this.openRemittances();  break;
            case "doctor":       this.openStock();        break;
            case "nurse":        this.openAppointments(); break;
            case "receptionist": this.openPatients();     break;
            case "billing":      this.openClaims();       break;
        }
    }
}

ClinicDashboard.template = "clinic_core.ClinicDashboard";
registry.category("actions").add("clinic_dashboard", ClinicDashboard);
