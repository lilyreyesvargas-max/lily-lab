{
    'name': 'Clinic Automation',
    'version': '17.0.1.0.0',
    'summary': 'Automated cron jobs for clinical operations',
    'category': 'Healthcare',
    'author': 'Clinic Dev',
    'depends': ['clinic_core'],
    'data': [
        'security/ir.model.access.csv',
        'data/clinic_automation_crons.xml',
        'views/clinic_automation_views.xml',
        'views/menu_views.xml',
    ],
    'demo': [],
    'installable': True,
    'auto_install': False,
    'application': False,
}
