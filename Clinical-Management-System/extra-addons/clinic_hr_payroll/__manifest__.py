{
    'name': 'Clinic HR & Payroll',
    'version': '17.0.1.0.0',
    'summary': 'HR: Employees by Branch, Clinic Roles, Medical Shifts',
    'category': 'Health',
    'author': 'Clinic Dev',
    'license': 'LGPL-3',
    'depends': ['clinic_core', 'hr'],
    'data': [
        'security/ir.model.access.csv',
        'data/clinic_shift_day_data.xml',
        'views/clinic_employee_views.xml',
        'views/clinic_shift_views.xml',
        'views/menu_views.xml',
    ],
    'demo': ['demo/clinic_hr_payroll_demo.xml'],
    'installable': True,
    'auto_install': False,
}
