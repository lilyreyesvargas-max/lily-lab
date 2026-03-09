{
    'name': 'Clinic Insurance & Billing',
    'version': '17.0.1.0.0',
    'summary': 'Insurance Coverage, Split Billing, Claims 837, Remittances 835',
    'category': 'Health',
    'author': 'Clinic Dev',
    'license': 'LGPL-3',
    'depends': ['clinic_patients', 'account'],
    'data': [
        'security/ir.model.access.csv',
        'data/clinic_billing_sequence.xml',
        'views/clinic_billing_views.xml',
        'views/menu_views.xml',
    ],
    'demo': ['demo/clinic_insurance_billing_demo.xml'],
    'installable': True,
    'auto_install': False,
}
