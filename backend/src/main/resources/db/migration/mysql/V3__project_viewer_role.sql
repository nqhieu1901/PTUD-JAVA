ALTER TABLE project_members DROP CHECK ck_project_members_role;

ALTER TABLE project_members ADD CONSTRAINT ck_project_members_role
    CHECK (project_role IN ('OWNER', 'MEMBER', 'VIEWER'));
