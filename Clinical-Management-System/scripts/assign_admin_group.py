#!/usr/bin/env python3
"""
Assign clinic_group_admin to the admin user after first Odoo install.
Usage: python3 assign_admin_group.py --url http://localhost:8069 --db odoo_clinic \
       --user admin@clinic.local --password admin_local_strong
"""
import argparse
import sys
import xmlrpc.client


def main():
    parser = argparse.ArgumentParser(description="Assign clinic_group_admin to admin user")
    parser.add_argument("--url", default="http://localhost:8069")
    parser.add_argument("--db", default="odoo_clinic")
    parser.add_argument("--user", default="admin@clinic.local")
    parser.add_argument("--password", default="admin_local_strong")
    args = parser.parse_args()

    try:
        common = xmlrpc.client.ServerProxy(f"{args.url}/xmlrpc/2/common")
        uid = common.authenticate(args.db, args.user, args.password, {})
        if not uid:
            print(f"[ERROR] Authentication failed for {args.user}")
            sys.exit(1)

        models = xmlrpc.client.ServerProxy(f"{args.url}/xmlrpc/2/object")

        # Search for clinic Administrator group
        grp = models.execute_kw(args.db, uid, args.password, "res.groups", "search",
            [[("name", "=", "Administrator"), ("category_id.name", "=", "Clinic")]])

        if not grp:
            print("[WARN] Group 'Clinic / Administrator' not found — module may not be installed yet")
            sys.exit(0)

        models.execute_kw(args.db, uid, args.password, "res.users", "write",
            [[uid], {"groups_id": [[4, grp[0]]]}])
        print(f"[OK] clinic_group_admin (id={grp[0]}) assigned to uid={uid}")

    except Exception as e:
        print(f"[ERROR] {e}")
        sys.exit(1)


if __name__ == "__main__":
    main()
