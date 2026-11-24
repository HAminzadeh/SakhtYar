-- Phase13 Builder workflow. ADDITIVE ONLY. Clone-preflight and explicit approval required.
-- Never run this migration automatically against the shared production database.
-- No existing business row DML; no email/SMS; no legal signature.
DO $preflight$
BEGIN
 IF to_regclass('public.builder_profile') IS NULL OR to_regclass('public.construction_case') IS NULL
    OR to_regclass('public.app_user') IS NULL OR to_regclass('public.project_builder') IS NULL
    OR to_regprocedure('public.sakhtyar_core_scope_ok(character varying,uuid)') IS NULL THEN
    RAISE EXCEPTION 'Builder Phase13 required tables or V60 REAL/TEST scope policy missing';
 END IF;
END $preflight$;

-- Fail closed on legacy case-insensitive duplicates; never silently rewrite REAL profiles.
DO $identity_duplicates$
BEGIN
 IF EXISTS (SELECT 1 FROM public.builder_profile WHERE national_identifier IS NOT NULL
     GROUP BY lower(national_identifier) HAVING count(*)>1) THEN
    RAISE EXCEPTION 'Builder national identifiers contain case-insensitive duplicates; manual clone remediation required';
 END IF;
END $identity_duplicates$;
CREATE UNIQUE INDEX ux_builder_profile_national_ci ON public.builder_profile(lower(national_identifier))
 WHERE national_identifier IS NOT NULL;

CREATE TABLE public.builder_account_binding (
 builder_id uuid PRIMARY KEY REFERENCES public.builder_profile(id) ON DELETE RESTRICT,
 username varchar(100) NOT NULL REFERENCES public.app_user(username) ON DELETE RESTRICT,
 review_reference varchar(500) NOT NULL CHECK (length(trim(review_reference)) >= 3),
 bound_by varchar(100) NOT NULL,
 bound_at timestamptz NOT NULL,
 UNIQUE(username)
);
CREATE UNIQUE INDEX ux_builder_binding_username_ci ON public.builder_account_binding(lower(username));

CREATE TABLE public.builder_verification_event (
 id uuid PRIMARY KEY,
 builder_id uuid NOT NULL REFERENCES public.builder_profile(id) ON DELETE RESTRICT,
 decision varchar(20) NOT NULL CHECK (decision IN ('APPROVED','REJECTED')),
 evidence_reference varchar(500) NOT NULL CHECK (length(trim(evidence_reference))>=3),
 reviewed_by varchar(100) NOT NULL,
 reviewed_at timestamptz NOT NULL
);
CREATE INDEX ix_builder_review_history ON public.builder_verification_event(builder_id,reviewed_at DESC);

CREATE TABLE public.builder_invitation (
 id uuid PRIMARY KEY,
 case_id uuid NOT NULL REFERENCES public.construction_case(id) ON DELETE RESTRICT,
 builder_id uuid NOT NULL REFERENCES public.builder_profile(id) ON DELETE RESTRICT,
 status varchar(24) NOT NULL CHECK (status IN ('PENDING','ACCEPTED','DECLINED','CANCELLED','EXPIRED')),
 expires_at timestamptz NOT NULL,
 created_by varchar(100) NOT NULL,
 created_at timestamptz NOT NULL,
 updated_at timestamptz NOT NULL,
 CONSTRAINT chk_builder_invitation_expiry CHECK (expires_at > created_at)
);
CREATE INDEX ix_builder_invitation_case ON public.builder_invitation(case_id,created_at DESC);
CREATE INDEX ix_builder_invitation_inbox ON public.builder_invitation(builder_id,created_at DESC);
CREATE UNIQUE INDEX ux_builder_invitation_single_pending ON public.builder_invitation(case_id,builder_id) WHERE status='PENDING';

CREATE TABLE public.builder_proposal (
 id uuid PRIMARY KEY,
 case_id uuid NOT NULL REFERENCES public.construction_case(id) ON DELETE RESTRICT,
 builder_id uuid NOT NULL REFERENCES public.builder_profile(id) ON DELETE RESTRICT,
 invitation_id uuid NOT NULL UNIQUE REFERENCES public.builder_invitation(id) ON DELETE RESTRICT,
 status varchar(24) NOT NULL CHECK (status IN ('SUBMITTED','CHANGES_REQUESTED','ACCEPTED','REJECTED','WITHDRAWN')),
 revision_no integer NOT NULL CHECK (revision_no > 0),
 proposed_share_percent numeric(7,4) NOT NULL CHECK (proposed_share_percent BETWEEN 0 AND 100),
 duration_months integer NOT NULL CHECK (duration_months BETWEEN 1 AND 240),
 terms jsonb NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(terms)='object'),
 created_at timestamptz NOT NULL,
 updated_at timestamptz NOT NULL
);
CREATE INDEX ix_builder_proposal_case ON public.builder_proposal(case_id,updated_at DESC);
CREATE UNIQUE INDEX ux_builder_proposal_one_accepted ON public.builder_proposal(case_id) WHERE status='ACCEPTED';

CREATE TABLE public.builder_proposal_revision (
 id uuid PRIMARY KEY,
 proposal_id uuid NOT NULL REFERENCES public.builder_proposal(id) ON DELETE RESTRICT,
 revision_no integer NOT NULL CHECK (revision_no>0),
 snapshot jsonb NOT NULL CHECK (jsonb_typeof(snapshot)='object'),
 recorded_by varchar(100) NOT NULL,
 recorded_at timestamptz NOT NULL,
 UNIQUE(proposal_id,revision_no)
);
CREATE INDEX ix_builder_proposal_revisions ON public.builder_proposal_revision(proposal_id,revision_no);

CREATE FUNCTION public.sakhtyar_builder_workflow_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE p_case uuid; p_builder uuid;
BEGIN
 IF TG_TABLE_NAME='builder_proposal_revision' THEN
   IF TG_OP<>'INSERT' THEN
     RAISE EXCEPTION 'Proposal revision history is append-only' USING ERRCODE='42501';
   END IF;
   SELECT case_id,builder_id INTO p_case,p_builder FROM public.builder_proposal WHERE id=NEW.proposal_id;
 ELSIF TG_TABLE_NAME='builder_proposal' THEN
   p_case:=NEW.case_id;p_builder:=NEW.builder_id;
   IF NOT EXISTS(SELECT 1 FROM public.builder_invitation i WHERE i.id=NEW.invitation_id
        AND i.case_id=p_case AND i.builder_id=p_builder AND i.status='ACCEPTED') THEN
     RAISE EXCEPTION 'Proposal invitation identity invalid' USING ERRCODE='23514';
   END IF;
 ELSIF TG_TABLE_NAME='builder_invitation' THEN
   p_case:=NEW.case_id;p_builder:=NEW.builder_id;
 END IF;
 IF p_case IS NULL OR NOT EXISTS (
   SELECT 1 FROM public.construction_case c WHERE c.id=p_case AND c.data_scope='REAL'
     AND c.test_workspace_id IS NULL AND public.sakhtyar_core_scope_ok(c.data_scope,c.test_workspace_id)) THEN
   RAISE EXCEPTION 'Builder workflow REAL case scope mismatch' USING ERRCODE='42501';
 END IF;
 IF TG_OP='UPDATE' AND (TG_TABLE_NAME='builder_invitation' OR TG_TABLE_NAME='builder_proposal') THEN
   IF OLD.id IS DISTINCT FROM NEW.id OR OLD.case_id IS DISTINCT FROM NEW.case_id
      OR OLD.builder_id IS DISTINCT FROM NEW.builder_id THEN
     RAISE EXCEPTION 'Builder workflow identity immutable' USING ERRCODE='42501';
   END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER trg_builder_invitation_scope BEFORE INSERT OR UPDATE ON public.builder_invitation
 FOR EACH ROW EXECUTE FUNCTION public.sakhtyar_builder_workflow_guard();
CREATE TRIGGER trg_builder_proposal_scope BEFORE INSERT OR UPDATE ON public.builder_proposal
 FOR EACH ROW EXECUTE FUNCTION public.sakhtyar_builder_workflow_guard();
CREATE TRIGGER trg_builder_revision_guard BEFORE INSERT OR UPDATE OR DELETE ON public.builder_proposal_revision
 FOR EACH ROW EXECUTE FUNCTION public.sakhtyar_builder_workflow_guard();

CREATE FUNCTION public.sakhtyar_builder_review_immutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 RAISE EXCEPTION 'Builder review record is append-only' USING ERRCODE='42501';
END $$;
CREATE TRIGGER trg_builder_review_immutable BEFORE UPDATE OR DELETE ON public.builder_verification_event
 FOR EACH ROW EXECUTE FUNCTION public.sakhtyar_builder_review_immutable();

-- Scope protection uses the existing V60 function; not a claim of per-user DB RLS.
ALTER TABLE public.builder_invitation ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.builder_invitation FORCE ROW LEVEL SECURITY;
CREATE POLICY builder_invitation_real_scope ON public.builder_invitation FOR ALL
 USING (EXISTS(SELECT 1 FROM public.construction_case c WHERE c.id=case_id AND c.data_scope='REAL'
    AND public.sakhtyar_core_scope_ok(c.data_scope,c.test_workspace_id)))
 WITH CHECK (EXISTS(SELECT 1 FROM public.construction_case c WHERE c.id=case_id AND c.data_scope='REAL'
    AND public.sakhtyar_core_scope_ok(c.data_scope,c.test_workspace_id)));
ALTER TABLE public.builder_proposal ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.builder_proposal FORCE ROW LEVEL SECURITY;
CREATE POLICY builder_proposal_real_scope ON public.builder_proposal FOR ALL
 USING (EXISTS(SELECT 1 FROM public.construction_case c WHERE c.id=case_id AND c.data_scope='REAL'
    AND public.sakhtyar_core_scope_ok(c.data_scope,c.test_workspace_id)))
 WITH CHECK (EXISTS(SELECT 1 FROM public.construction_case c WHERE c.id=case_id AND c.data_scope='REAL'
    AND public.sakhtyar_core_scope_ok(c.data_scope,c.test_workspace_id)));
ALTER TABLE public.builder_proposal_revision ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.builder_proposal_revision FORCE ROW LEVEL SECURITY;
CREATE POLICY builder_proposal_revision_real_scope ON public.builder_proposal_revision FOR ALL
 USING (EXISTS(SELECT 1 FROM public.builder_proposal p JOIN public.construction_case c ON c.id=p.case_id
    WHERE p.id=proposal_id AND c.data_scope='REAL' AND public.sakhtyar_core_scope_ok(c.data_scope,c.test_workspace_id)))
 WITH CHECK (EXISTS(SELECT 1 FROM public.builder_proposal p JOIN public.construction_case c ON c.id=p.case_id
    WHERE p.id=proposal_id AND c.data_scope='REAL' AND public.sakhtyar_core_scope_ok(c.data_scope,c.test_workspace_id)));

REVOKE ALL ON public.builder_account_binding,public.builder_verification_event,
 public.builder_invitation,public.builder_proposal,public.builder_proposal_revision FROM PUBLIC;
REVOKE ALL ON FUNCTION public.sakhtyar_builder_workflow_guard(),
 public.sakhtyar_builder_review_immutable() FROM PUBLIC;
COMMENT ON TABLE public.builder_verification_event IS 'Manual administrative review record, not a certified builder qualification.';
COMMENT ON TABLE public.builder_proposal_revision IS 'Technical append-only revision history, not a legal signature or signed document.';
