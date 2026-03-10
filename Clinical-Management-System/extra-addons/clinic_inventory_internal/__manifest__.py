{
    'name': 'Clinic Inventory Internal',
    'version': '17.0.1.0.0',
    'summary': 'Internal Supply Management: Request → Approval → Transfer → Consumption',
    'category': 'Health',
    'author': 'Clinic Dev',
    'license': 'LGPL-3',
    'depends': ['clinic_core', 'stock'],
    'data': [
        'security/ir.model.access.csv',
        'security/record_rules.xml',
        'data/clinic_supply_sequence.xml',
        'views/clinic_supply_request_views.xml',
        'views/menu_views.xml',
    ],
    'demo': ['demo/clinic_inventory_internal_demo.xml'],
    'installable': True,
    'auto_install': False,
}
