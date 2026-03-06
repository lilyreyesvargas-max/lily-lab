{
    'name': 'Customer Loyalty Points',
    'version': '17.0.1.0.0',
    'category': 'Sales/Sales',
    'summary': 'Manage customer loyalty points, rewards, and redemption.',
    'description': """
        Loyalty Points System:
        - Rules-based point earning.
        - Points redemption as discounts in Sale Orders.
        - Customer points history and balance.
        - PDF summary report.
    """,
    'author': 'Lily Reyes',
    'website': 'https://github.com/lilyreyesvargas-max',
    'depends': ['base', 'contacts', 'sale_management'],
    'data': [
        'security/loyalty_security.xml',
        'security/ir.model.access.csv',
        'views/loyalty_rule_views.xml',
        'views/loyalty_transaction_views.xml',
        'views/res_partner_views.xml',
        'views/sale_order_views.xml',
        'views/loyalty_menu.xml',
        'report/loyalty_report.xml',
        'report/loyalty_report_templates.xml',
    ],
    'demo': [],
    'installable': True,
    'application': True,
    'auto_install': False,
    'license': 'LGPL-3',
}
