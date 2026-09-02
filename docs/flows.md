# OID4VCI and OID4VP flows

## Accepting offers and presentation requests

```java
// Accept a credential offer (returns OfferResponse)
OfferResponse offer = wallet.acceptCredentialOffer(credentialOfferUri);

// Accept a presentation request (returns PresentationResponse with redirect URI)
PresentationResponse presentation = wallet.acceptPresentationRequest(presentationRequestUri);
String redirectUri = presentation.redirectUri();
String idToken = presentation.idToken(); // set when response_type includes id_token
```

An offer can require a transaction code that the issuer delivers separately (OpenID4VCI §4.1.1). The wallet refuses such an offer without the code, so pass it along:

```java
OfferResponse response = wallet.acceptCredentialOffer(offerUri, "1234");
```

On a wallet without auto-accept, the code travels with the approval instead: `ConsentApproval.approval().txCode("1234")`, see [Approving with a specific selection](#approving-with-a-specific-selection).

Offers can also be delivered to the wallet's own URL instead of the `openid-credential-offer://` custom scheme, for issuers that only speak plain web URLs:

```java
String offerEndpoint = wallet.getCredentialOfferUrl(); // http://host:port/credential-offer
```

## Consent handling

By default the wallet accepts every offer and presentation request. Start it with `withoutAutoAccept()` to make it wait for an explicit decision instead.

`acceptCredentialOffer(...)` and `acceptPresentationRequest(...)` always accept, even on a wallet started with `withoutAutoAccept()`. The API call itself is the caller's decision. Requests arriving through interactive channels (the wallet's `/credential-offer` and `/authorize` URLs, or a custom-scheme dispatch) wait as pending consent requests instead.

To exercise the consent dialog from a test, submit the offer or request as an interactive one. The wallet holds the submission open until it is decided, so the result arrives as a `CompletableFuture`:

```java
WalletClient client = wallet.client();

CompletableFuture<OfferResponse> submitted = client.submitCredentialOfferForConsent(offerUri);

ConsentRequest request = client.awaitPendingRequest(Duration.ofSeconds(10));
ApprovalResult result = client.approveRequest(request.id());
String redirectUri = result.redirectUri();

OfferResponse response = submitted.get();
```

`submitPresentationRequestForConsent(...)` does the same for OID4VP requests. Denying resolves the submission with a `denied` status instead:

```java
client.denyRequest(request.id());
```

`getPendingRequests()` lists everything currently awaiting a decision.

### What a pending request offers

A pending `ConsentRequest` carries what the wallet's consent dialog would show:

- `matchedCredentials()` holds the credentials the wallet would present on its own, one `CredentialMatch` per DCQL credential query, including the claims it would disclose.
- `credentialOptions()` holds every credential that could answer each query, plus the request's credential set options when the verifier offered alternatives (for example SD-JWT or mdoc).
- `purposes()` holds what the verifier registered the data request for, when its registration certificate names purposes.

### Approving with a specific selection

`approveRequest(id, ConsentApproval)` controls what the approval presents. An empty approval keeps the wallet's own selection, exactly like `approveRequest(id)`:

```java
CompletableFuture<PresentationResponse> submitted =
        client.submitPresentationRequestForConsent(requestUri);
ConsentRequest pending = client.awaitPendingRequest(Duration.ofSeconds(10));

// present another matching credential for the "pid" query,
// disclosing only its given_name
ConsentQueryOptions query = pending.credentialOptions().query("pid");
String alternative = query.candidates().get(1).credentialId();

ApprovalResult result = client.approveRequest(pending.id(), ConsentApproval.approval()
        .pickCredential("pid", alternative)
        .selectClaims(alternative, "given_name"));

PresentationResponse response = submitted.get();
```

- `pickCredential(queryId, credentialId)` presents one of the query's `candidates()` instead of the wallet's first choice.
- `selectClaims(credentialId, claims...)` discloses only the named claims of that credential. Credentials without an entry disclose everything the request matched.
- `chooseSetOption(setIndex, optionIndex)` answers a credential set with another of its `options()`, for example the mdoc PID instead of the SD-JWT one. `skipOptionalSet(setIndex)` skips a set the verifier marked optional.
- `txCode(code)` supplies the transaction code for an issuance offer that requires one.

The wallet validates the selection against the pending request. An approval referencing a credential or option the request did not offer is rejected and the request stays pending.

## Presentation during issuance (OpenID4VCI 1.1)

An issuer can make a presentation the condition of issuance: instead of a browser login, its authorization server publishes an `authorization_challenge_endpoint` and demands a credential the wallet already holds. On a wallet started with `withVciVersion(VciVersion.V1_1)`, redeeming such an offer needs nothing extra, the wallet presents and receives in one call:

```java
EudiWalletContainer wallet = new EudiWalletContainer()
    .withVciVersion(VciVersion.V1_1);

OfferResponse response = wallet.acceptCredentialOffer(offerUri);

// the wallet presented its PID to the issuer and holds the new credential
assertThat(wallet.client().hasCredentialWithType("urn:example:ticket:1")).isTrue();
```

Without auto-accept, the flow raises two consent requests, because approving the offer is not consent to disclose a credential. The offer comes first, then the presentation the issuer asked for, with its own type `issuance_presentation`. Approve the offer with `approveRequestAsync`: its result only arrives once the presentation request is answered too, so a synchronous approval would block until the wallet gives up on it:

```java
CompletableFuture<OfferResponse> submitted = client.submitCredentialOfferForConsent(offerUri);

ConsentRequest offer = client.awaitPendingRequest(Duration.ofSeconds(10));
// offer.type() is "issuance"
CompletableFuture<ApprovalResult> offerApproved = client.approveRequestAsync(offer.id());

ConsentRequest presentation = client.awaitPendingRequest(Duration.ofSeconds(10));
// presentation.type() is "issuance_presentation", with matchedCredentials()
// and credentialOptions() like any presentation request
client.approveRequest(presentation.id());

offerApproved.get();
OfferResponse response = submitted.get();
```

The presentation request takes a `ConsentApproval` like any other, so a test can choose the presented credential or disclose only selected claims here too. Denying it aborts the issuance.
