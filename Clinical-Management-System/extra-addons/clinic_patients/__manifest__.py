{
    'name': 'Clinic Patients',
    'version': '17.0.1.0.0',
    'summary': 'Patient Management — Patients, Insurers, Policies, Consents',
    'category': 'Health',
    'author': 'Clinic Dev',
    'license': 'LGPL-3',
    'depends': ['clinic_core'],
    'data': [
        'security/ir.model.access.csv',
        'data/clinic_patient_sequence.xml',
        'views/clinic_patient_views.xml',
        'views/clinic_insurer_views.xml',
        'views/menu_views.xml',
    ],
    'demo': ['demo/clinic_patients_demo.xml'],
    'installable': True,
    'auto_install': False,
}
