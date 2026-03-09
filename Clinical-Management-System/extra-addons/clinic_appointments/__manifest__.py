{
    'name': 'Clinic Appointments',
    'version': '17.0.1.0.0',
    'summary': 'Appointment Scheduling by Branch — Calendar, Kanban, List',
    'category': 'Health',
    'author': 'Clinic Dev',
    'license': 'LGPL-3',
    'depends': ['clinic_patients'],
    'data': [
        'security/ir.model.access.csv',
        'data/clinic_appointment_sequence.xml',
        'views/clinic_appointment_views.xml',
        'views/menu_views.xml',
    ],
    'demo': ['demo/clinic_appointments_demo.xml'],
    'installable': True,
    'auto_install': False,
}
