# Tredjepartslisenser

Avhengigheter med spesielle lisensvilkår (resten er vanlige MIT/Apache-pakker, se `package.json` og `pom.xml`).

## @garmin/fitsdk (Garmin FIT JavaScript SDK)

Brukes i nettleseren til å lese Garmin FIT-filer ved import av treningsøkter (`frontend/src/garminFit.js`).
Lastes som en egen kodebit (lazy-load) først når man importerer FIT/ZIP.

Lisens: *Flexible and Interoperable Data Transfer (FIT) Protocol License Agreement* (Garmin), se
`frontend/node_modules/@garmin/fitsdk/LICENSE.txt`. Den tillater bruk av FIT-protokollen i egen programvare, men er
IKKE en åpen kildekode-lisens (blant annet: ingen fjerning av Garmins merknader, ingen videreutdeling av SDK-en
som eget produkt). Dette bør du lese selv før appen tilbys til andre enn deg selv, spesielt hvis den skal brukes
kommersielt. Alternativet er å skrive en egen FIT-leser (protokollen er dokumentert) og bare bruke SDK-en i tester.

## fflate

MIT. Brukes til å pakke ut zip-filer strømmende i nettleseren.
