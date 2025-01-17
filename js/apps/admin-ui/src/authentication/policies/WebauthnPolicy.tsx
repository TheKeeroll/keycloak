import type RealmRepresentation from "@keycloak/keycloak-admin-client/lib/defs/realmRepresentation";
import {
  ActionGroup,
  AlertVariant,
  Button,
  ButtonVariant,
  FormGroup,
  PageSection,
  Popover,
  SelectVariant,
  Text,
  TextContent,
} from "@patternfly/react-core";
import { QuestionCircleIcon } from "@patternfly/react-icons";
import { useEffect } from "react";
import { FormProvider, useForm } from "react-hook-form";
import { useTranslation } from "react-i18next";
import {
  HelpItem,
  SelectControl,
  SwitchControl,
  TextControl,
  useHelp,
} from "ui-shared";
import { adminClient } from "../../admin-client";
import { useAlerts } from "../../components/alert/Alerts";
import { FormAccess } from "../../components/form/FormAccess";
import { MultiLineInput } from "../../components/multi-line-input/MultiLineInput";
import { TimeSelectorControl } from "../../components/time-selector/TimeSelectorControl";
import { useRealm } from "../../context/realm-context/RealmContext";
import { convertFormValuesToObject, convertToFormValues } from "../../util";

import "./webauthn-policy.css";

const SIGNATURE_ALGORITHMS = [
  "ES256",
  "ES384",
  "ES512",
  "RS256",
  "RS384",
  "RS512",
  "RS1",
] as const;
const ATTESTATION_PREFERENCE = [
  "not specified",
  "none",
  "indirect",
  "direct",
] as const;

const AUTHENTICATOR_ATTACHMENT = [
  "not specified",
  "platform",
  "cross-platform",
] as const;

const RESIDENT_KEY_OPTIONS = ["not specified", "Yes", "No"] as const;

const USER_VERIFY = [
  "not specified",
  "required",
  "preferred",
  "discouraged",
] as const;

type WeauthnSelectProps = {
  name: string;
  label: string;
  options: readonly string[];
  labelPrefix?: string;
  isMultiSelect?: boolean;
};

const WebauthnSelect = ({
  name,
  label,
  options,
  labelPrefix,
  isMultiSelect = false,
}: WeauthnSelectProps) => {
  const { t } = useTranslation();
  return (
    <SelectControl
      name={name}
      label={t(label)}
      variant={
        isMultiSelect ? SelectVariant.typeaheadMulti : SelectVariant.single
      }
      controller={{ defaultValue: options[0] }}
      options={options.map((option) => ({
        key: option,
        value: labelPrefix ? t(`${labelPrefix}.${option}`) : option,
      }))}
      typeAheadAriaLabel={t(name)}
    />
  );
};

type WebauthnPolicyProps = {
  realm: RealmRepresentation;
  realmUpdated: (realm: RealmRepresentation) => void;
  isPasswordLess?: boolean;
};

export const WebauthnPolicy = ({
  realm,
  realmUpdated,
  isPasswordLess = false,
}: WebauthnPolicyProps) => {
  const { t } = useTranslation();
  const { addAlert, addError } = useAlerts();
  const { realm: realmName } = useRealm();
  const { enabled } = useHelp();
  const form = useForm({ mode: "onChange" });
  const {
    setValue,
    handleSubmit,
    formState: { isDirty },
  } = form;

  const namePrefix = isPasswordLess
    ? "webAuthnPolicyPasswordless"
    : "webAuthnPolicy";

  const setupForm = (realm: RealmRepresentation) =>
    convertToFormValues(realm, setValue);

  useEffect(() => setupForm(realm), []);

  const onSubmit = async (realm: RealmRepresentation) => {
    const submittedRealm = convertFormValuesToObject(realm);
    try {
      await adminClient.realms.update({ realm: realmName }, submittedRealm);
      realmUpdated(submittedRealm);
      setupForm(submittedRealm);
      addAlert(t("webAuthnUpdateSuccess"), AlertVariant.success);
    } catch (error) {
      addError("webAuthnUpdateError", error);
    }
  };

  return (
    <PageSection variant="light">
      {enabled && (
        <Popover bodyContent={t(`${namePrefix}FormHelp`)}>
          <TextContent className="keycloak__section_intro__help">
            <Text>
              <QuestionCircleIcon /> {t("webauthnIntro")}
            </Text>
          </TextContent>
        </Popover>
      )}

      <FormAccess
        role="manage-realm"
        isHorizontal
        onSubmit={handleSubmit(onSubmit)}
        className="keycloak__webauthn_policies_authentication__form"
      >
        <FormProvider {...form}>
          <TextControl
            name={`${namePrefix}RpEntityName`}
            label={t("webAuthnPolicyRpEntityName")}
            labelIcon={t("webAuthnPolicyRpEntityNameHelp")}
            rules={{ required: { value: true, message: t("required") } }}
          />
          <WebauthnSelect
            name={`${namePrefix}SignatureAlgorithms`}
            label="webAuthnPolicySignatureAlgorithms"
            options={SIGNATURE_ALGORITHMS}
            isMultiSelect
          />
          <TextControl
            name={`${namePrefix}RpId`}
            label={t("webAuthnPolicyRpId")}
            labelIcon={t("webAuthnPolicyRpIdHelp")}
          />
          <WebauthnSelect
            name={`${namePrefix}AttestationConveyancePreference`}
            label="webAuthnPolicyAttestationConveyancePreference"
            options={ATTESTATION_PREFERENCE}
            labelPrefix="attestationPreference"
          />
          <WebauthnSelect
            name={`${namePrefix}AuthenticatorAttachment`}
            label="webAuthnPolicyAuthenticatorAttachment"
            options={AUTHENTICATOR_ATTACHMENT}
            labelPrefix="authenticatorAttachment"
          />
          <WebauthnSelect
            name={`${namePrefix}RequireResidentKey`}
            label="webAuthnPolicyRequireResidentKey"
            options={RESIDENT_KEY_OPTIONS}
            labelPrefix="residentKey"
          />
          <WebauthnSelect
            name={`${namePrefix}UserVerificationRequirement`}
            label="webAuthnPolicyUserVerificationRequirement"
            options={USER_VERIFY}
            labelPrefix="userVerify"
          />
          <TimeSelectorControl
            name={`${namePrefix}CreateTimeout`}
            label={t("webAuthnPolicyCreateTimeout")}
            labelIcon={t("otpPolicyPeriodHelp")}
            units={["second", "minute", "hour"]}
            controller={{
              defaultValue: 0,
              rules: {
                min: 0,
                max: {
                  value: 31536,
                  message: t("webAuthnPolicyCreateTimeoutHint"),
                },
              },
            }}
          />
          <SwitchControl
            name={`${namePrefix}AvoidSameAuthenticatorRegister`}
            label={t("webAuthnPolicyAvoidSameAuthenticatorRegister")}
            labelIcon={t("webAuthnPolicyAvoidSameAuthenticatorRegisterHelp")}
            labelOn={t("on")}
            labelOff={t("off")}
          />
          <FormGroup
            label={t("webAuthnPolicyAcceptableAaguids")}
            fieldId="webAuthnPolicyAcceptableAaguids"
            labelIcon={
              <HelpItem
                helpText={t("webAuthnPolicyAcceptableAaguidsHelp")}
                fieldLabelId="webAuthnPolicyAcceptableAaguids"
              />
            }
          >
            <MultiLineInput
              name={`${namePrefix}AcceptableAaguids`}
              aria-label={t("webAuthnPolicyAcceptableAaguids")}
              addButtonLabel="addAaguids"
            />
          </FormGroup>
          <FormGroup
            label={t("webAuthnPolicyExtraOrigins")}
            fieldId="webAuthnPolicyExtraOrigins"
            labelIcon={
              <HelpItem
                helpText={t("webAuthnPolicyExtraOriginsHelp")}
                fieldLabelId="webAuthnPolicyExtraOrigins"
              />
            }
          >
            <MultiLineInput
              name={`${namePrefix}ExtraOrigins`}
              aria-label={t("webAuthnPolicyExtraOrigins")}
              addButtonLabel="addOrigins"
            />
          </FormGroup>
        </FormProvider>

        <ActionGroup>
          <Button
            data-testid="save"
            variant="primary"
            type="submit"
            isDisabled={!isDirty}
          >
            {t("save")}
          </Button>
          <Button
            data-testid="reload"
            variant={ButtonVariant.link}
            onClick={() => setupForm(realm)}
          >
            {t("reload")}
          </Button>
        </ActionGroup>
      </FormAccess>
    </PageSection>
  );
};
