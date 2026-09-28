# Savvy Store Policy Risks

Status date: 25 September 2026. Nothing here means "approved". Approval is known only after submission.

**Verification note:**
- Apple sources were read directly.
- Google Play policy pages could not be opened from the R&D environment, so Play items rely on official page excerpts.
- **Action:** a team member must open these Play pages in a browser and confirm the wording before the Android architecture is frozen:
  - https://support.google.com/googleplay/android-developer/answer/10964491 (Accessibility API)
  - https://support.google.com/googleplay/android-developer/answer/16558241 (User data / permissions)
  - https://support.google.com/googleplay/android-developer/answer/10158779 (QUERY_ALL_PACKAGES)
  - https://support.google.com/googleplay/android-developer/answer/12955211 (isMonitoringTool)
  - https://support.google.com/googleplay/android-developer/answer/9888380 (Stalkerware / monitoring)
  - https://support.google.com/googleplay/android-developer/answer/13392821 (Foreground service declaration)

## Apple App Store

| Item | Requirement | Savvy status | Risk |
| --- | --- | --- | --- |
| Family Controls entitlement | Distribution approval for app **and each of 4 extensions** | Not yet requested | **High schedule risk** (days to 4.5+ weeks reported). Request immediately |
| DPLA 3.3.3(P) Family Controls | Primary purpose must be family controls or individual focus and productivity. No "organizational settings". No managing another adult's device. Data only for those controls, not ads or data brokers | Fits for MVP | Low for MVP. **Medium for future institute/coaching features** ("organizational settings") |
| Guideline 4.10 | "You may not monetize ... Screen Time APIs" | Subscriptions out of MVP scope | Future paywall must sell Savvy features, not access to blocking itself |
| Guideline 5.1.1 / 5.1.2 | Privacy policy, consent, minimisation, account deletion, no use of app-install data for ads | Backend stores account, card, focus data only | Low; needs privacy policy and account deletion |
| Kids category (1.3, 5.1.4) | No third-party analytics or ads, COPPA / GDPR | Recommend listing for adults/parents, not Kids category | Low if not in Kids category |
| NFC | `NFCReaderUsageDescription`, entitlement value `TAG` | In project.yml | Low |
| Camera | `NSCameraUsageDescription` | In project.yml | Low |
| App Privacy label | Declare account identifiers, card identifier, focus data | To prepare | Low |
| Guideline 2.5.11 (App Intents) | Intents must resolve directly | Intents only start focus or open scan | Low |
| MDM (5.5) | Only enterprises, education, government, limited parental-control companies | Not used | Not applicable |

## Google Play

| Item | Requirement | Savvy status | Risk |
| --- | --- | --- | --- |
| Accessibility API (non-tool) | Play Console declaration, in-app prominent disclosure, affirmative consent, video, listing disclosure. `isAccessibilityTool=false` | Disclosure + consent implemented; config false | **Medium**: reviewers may say UsageStats is enough. Fallback path exists |
| Accessibility to prevent disable / uninstall | Prohibited **unless authorized by a parent or guardian through a parental control app** | Guard enabled only in PARENT mode (unit-tested) | Self mode: must not add. Parent mode: allowed, must be clearly a parental-control flow |
| Accessibility for AI agents | Prohibited (2025) | Not used; deterministic rules only | Low |
| Foreground service `specialUse` | Manifest property + Play declaration + video | Implemented | Medium (review of justification) |
| PACKAGE_USAGE_STATS | User grant in Settings; no Play declaration found | Implemented | Low to medium |
| SYSTEM_ALERT_WINDOW | "Very few apps should use this" | Used for BAL exemption in UsageStats path | Medium; could drop if Accessibility path is approved |
| QUERY_ALL_PACKAGES | Restricted | **Not used**; launcher `<queries>` instead | Low |
| REQUEST_IGNORE_BATTERY_OPTIMIZATIONS | Only where core function affected; family safety listed | Settings screen link only | Low if not using the direct dialog in self mode |
| Device admin | Legacy; policies deprecated for non-enterprise; no policies used | Parent mode only | Medium; confirm no restriction for parental control |
| Stalkerware / monitoring | Parental monitoring apps: persistent notification, disclosure, unique icon, not hidden | FGS notification present | Required for parent mode |
| `isMonitoringTool` meta-data | `child_monitoring` if child data goes to parent | Commented in manifest | Must add in parent-mode release if usage or app lists are shared |
| Families policy | If children are target audience | Recommend adult/parent audience | Low if audience is adults |
| Data safety form | Declare collected data | To prepare | Low |
| Target API | API 36 for new apps from 31 Aug 2026 | targetSdk 36 | Low |
| Developer verification | Registration required for certified devices in some countries from Sep 2026, global 2027 | Play distribution unaffected | Low |

## Features that could risk rejection

1. Calling self-use mode "uninstall protection" on either store. Use "commitment lock" wording instead.
2. Any Accessibility use that blocks Settings, deactivation or uninstall in self mode on Android.
3. Future institute or batch features using Family Controls in "organizational settings" on iOS.
4. A paywall that locks the basic blocking itself behind payment on iOS (4.10).
5. Uploading iOS Screen Time data. It is technically impossible outside the EU anyway, but do not attempt workarounds.
