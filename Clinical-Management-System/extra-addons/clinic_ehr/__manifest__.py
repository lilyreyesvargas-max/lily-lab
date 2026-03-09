{
    'name': 'Clinic EHR',
    'version': '17.0.1.0.0',
    'summary': 'Electronic Health Records — Encounters, Diagnoses, Specialty Notes',
    'category': 'Health',
    'author': 'Clinic Dev',
    'license': 'LGPL-3',
    'depends': ['clinic_patients'],
    'data': [
        'security/ir.model.access.csv',
        'data/clinic_ehr_sequence.xml',
        'views/clinic_encounter_views.xml',
        'views/menu_views.xml',
    ],
    'demo': ['demo/clinic_ehr_demo.xml'],
    'installable': True,
    'auto_install': False,
}
