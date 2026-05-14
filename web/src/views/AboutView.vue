<template>
  <div class="about-view">
    <h1>Uitleg voor dummies</h1>

    <about-item>
      <template #header>Wat is deze?</template>
      In het kort: een persoonlijk hobby-project dat ooit begon met de vraag "Wat is onze kans om kampioen te worden?".<br>
      Het resultaat is een enorme wirwar van mooie getalletjes, kansberekeningen en competitie-simulaties.<br>
      Voor het lange antwoord, opgeschreven in een melige bui: lees hieronder verder.
    </about-item>

    <about-item>
      <template #header>Wat zijn al die mooie getalletjes?</template>
      Dat zijn de sterke-inschattingen, oftewel ratings, van alle eerste teams van korfbalverenigingen in Nederland.<br>
      Hoe hoger de rating, hoe sterker het team. Simpel toch? In de regel is het gemiddelde van alle teams 1500.
    </about-item>

    <about-item>
      <template #header>Ja, maar hoe kom je dan aan die getalletjes?</template>
      Door een enorme lading wedstrijden (meer dan 300.000) in een slim computerprogrammatje te zetten en deze allerlei
      ingewikkelde berekeningen te laten doen. Het resultaat hiervan zijn de ratings van elk Nederlands korfbalteam, door
      de tijd heen beginnend bij het jaar 1903. Klik maar op een teamnaam, dan zie je de sterkte van dat team door de jaren heen.
    </about-item>

    <about-item>
      <template #header>Ingewikkelde berekeningen, hoe zit dat?</template>
      Moeilijk uit te leggen, want, tja, ze zijn nogal ingewikkeld, weet je...<br>
      De basis is dat er bij elke wedstrijd een voorspelling wordt gemaakt. Presteert een team beter dan deze
      voorspelling, dan wordt de rating hoger, anders wordt deze lager. Hoeveel de rating verandert hangt onder andere
      af van hoeveel de voorspelling ernaast zit.<br>
      Let wel: alleen de uitslag van de wedstrijd wordt gebruikt. Informatie over spelers(-wissels), klasse,
      competitie-stand en het 'belang' van wedstrijden wordt niet meegenomen.
    </about-item>

    <about-item>
      <template #header>Oke boeiend, maar wat kun je hier nou mee?</template>
      Antwoord krijgen op de volgende vragen:<br>
      Welk team was in 1969 het sterkste?<br>
      Hoe groot is de kans dat Team A van Team B wint?<br>
      Hoe groot is de kans dat Team X kampioen wordt, promoveert of degradeert?<br>
      Op welk moment in haar geschiedenis was Team X het sterkst/zwakst?<br>
      Op al deze vragen kun je nu antwoord krijgen!
    </about-item>

    <about-item>
      <template #header>Leuk, maar waar vind ik al die onderdelen op de site dan?</template>
      Kort overzichtje van wat je in het menu vindt:<br>
      <ul>
        <li><b>Home</b>: korte introductie.</li>
        <li><b>Uitleg</b>: deze pagina vol droge humor en net iets te veel tekst.</li>
        <li><b>Seizoenen + Archief</b>: per seizoen de competities, poules, standen, kansen, uitslagen en programma.</li>
        <li><b>Mega grafiek</b>: één grote grafiek waarin je teams kunt filteren op naam, plaats of klasse (met/zonder voorgangers).</li>
        <li><b>Statistieken</b>: records uit alle wedstrijden, standaard voor dit seizoen, maar ook voor elke zelfgekozen periode of gewoon de hele geschiedenis.</li>
        <li><b>Data</b>: export van alle wedstrijden als CSV, inclusief kolomuitleg. Ideaal als je graag Excel pijn doet.</li>
        <li><b>Changelog</b>: wat er recent aan de site veranderd is.</li>
      </ul>
      En verder kun je vanuit team- en poulepagina's doorklikken naar extra's zoals teamstatistieken, onderlinge geschiedenis en de simulator.
    </about-item>

    <about-item>
      <template #header>Oke das best gaaf, kom nou toch maar op met die berekeningen, hoe zit dan?</template>
      Oke vooruit dan maar. Niet huilen als je het niet snapt...
    </about-item>

    <button class="math-button" @click="showMath = !showMath; hasShownMath = true">
      <template v-if="showMath">
        Aaah ik snap er geen bal van, weg met die wiskunde!
      </template>
      <template v-else>
        Kom maar op met die wiskunde
      </template>
    </button>
    <template v-if="showMath || hasShownMath">
      <div v-show="showMath" style="display: contents">
      <about-item>
        <template #header>Begrepen, ik zal niet huilen</template>
        Nou komtie.<br>
        De basis is het Elo systeem,
        (nee, <a href="https://youtu.be/j4dMnAPZu70" target="_blank">niet de band...</a>)
        een methode oorspronkelijk ontwikkeld voor schaken.<br>
        Kijk <a href="https://www.youtube.com/watch?v=inXUp5j107I" target="_blank">hier</a>
        voor een mooie uitleg. Of lees verder voor mijn eigen gebrekkige uitleg... <br>
        Het uitgangspunt is de volgende formule:<br>
        <br>
        <tex f="E_A=\frac{1}{1+10^\frac{R_B - R_A}{400}}" />
        <br>
        Hierbij is <tx f="R_A" /> de rating van Team A, <tx f="R_B" /> de rating van Team B en <tx f="E_A" /> het
        verwachte resultaat voor Team A. <tx f="E_A" /> is een getal tussen 0 en 1 en geeft de kans op winst aan (wanneer geen gelijkspel wordt toegestaan)
        Bij <tx f="E_A=0.75" /> is het een 75% kans op winst, bij <tx f="E_A=0.25" /> is het een 25% kans op winst enz...
        <br>
      </about-item>

      <about-item>
        <template #header>Oke, oke, maar hoe pas je de ratings aan na een wedstrijd?</template>
        Na elke wedstrijd wordt het verwachte resultaat <tx f="E_A" /> vergeleken met de daadwerkelijke uitkomst <tx f="S_A" />.
        Is <tx f="S_A" /> groter dan <tx f="E_A" /> dan heeft Team A beter gepresteerd dan verwacht, en gaat de rating omhoog, en anders gaat deze omlaag.
      </about-item>

      <about-item>
        <template #header>Oke maar hoe wordt <tx f="S_A" /> berekend?</template>
        Normaal staat 1 voor winst, 0.5 voor gelijkspel en 0 voor verlies.<br>
        Maar zo zwart-wit is korfbal natuurlijk niet, winnen kun je met 1 punt verschil, maar ook met 20 punten verschil.
        De winstmarge wordt meegenomen in de berekening van <tx f="S_A" />:<br>
        Een gelijkspel is nog steeds 0.5, maar een winst is een <tx f="S_A" /> van ergens tussen 0.5 en 1. Hoe hoger de winst, hoe dichter bij de 1.
        Bij verlies geldt hetzelfde, maar dan ligt <tx f="S_A" /> onder 0.5 en benadert 0 bij hogere winstmarges<br>
      </about-item>

      <about-item>
        <template #header>Oke maar nu weet ik nog niet hoe <tx f="S_A" /> precies berekend wordt?</template>
        Daar is het nog een beetje te vroeg voor... Deze bewaar ik voor later...
      </about-item>

      <about-item>
        <template #header>En je hebt ook nog niet precies gezegd hoe ratings worden aangepast na een wedstrijd?</template>
        Normaal wordt het verschil tussen verwachte en werkelijke uitkomst (<tx f="S_A-E_A" />)
        vermenigvuldigd met een factor <tx f="K" />. Het resultaat wordt bij de rating opgeteld of afgetrokken:<br>
        <tex f="R^\prime_A=R_A + (S_A-E_A)*K" />
        <br>
        Echter wordt hier het Glicko-2 systeem gebruikt, waarbij het 'iets' ingewikkelder werkt.
      </about-item>

      <about-item>
        <template #header>Glicko? Dat zijn die afvalcontainers toch?</template>
        Nee, dat is Kliko, lijkt erop...<br>
        Glicko-2 is een uitbreiding van het Elo-systeem.
        Het Glicko-2 systeem voegt de mate van <i>onzekerheid</i> (rating deviation, afgekort <tx f="RD" />) van de rating van een team toe aan de berekening.
        Bij een hoge <tx f="RD" /> kan de daadwerkelijke rating veel lager of hoger zijn dan de geschatte rating; de geschatte rating is dus onbetrouwbaar.
        Een lage <tx f="RD" /> geeft aan dat de rating betrouwbaarder is.<br>
      </about-item>

      <about-item>
        <template #header>Zucht, weer een nieuw getal... Hoe wordt <tx f="RD" /> berekend?</template>
        Kort samengevat: <tx f="RD"/> wordt kleiner bij het spelen van wedstrijden, maar wordt ook elke dag iets hoger.
        De gedachte is dat het spelen van wedstrijden de betrouwbaarheid van de rating verhoogt, terwijl door het verstrijken van de tijd de betrouwbaarheid weer afneemt.<br>
        Wil je precies weten hoe dit berekend wordt? <a href="http://www.glicko.net/glicko/glicko2.pdf">Hier</a>, succes...<br>
      </about-item>

      <about-item>
        <template #header>Wat hebben we nou aan die <tx f="RD" />?</template>
        Heel simpel: bij een hoge <tx f="RD"/> wordt de rating sneller aangepast dan bij een lage <tx f="RD"/>.<br>
        Dit heeft als gevolg dat ratings na een periode van rust (bijvoorbeeld na de zomerstop) sneller worden aangepast dan midden in een seizoen.<br>
      </about-item>

      <about-item>
        <template #header>Je zou nog uitleggen hoe <tx f="S_A" /> nou precies berekend wordt?</template>
        Oke, vooruit.<br>
        Van elk team wordt het scorend vermogen μ bijgehouden: het verwachte aantal doelpunten dat een team maakt tegen een ongeveer even sterke tegenstander.<br>
        Vervolgens nemen we aan dat het aantal doelpunten van een team normaal verdeeld is: <tx f="X\sim N(\mu_X,\sigma_X^2)" />.<br>
        In het model geldt (ongeveer) <tx f="\sigma_X=0.166\mu_X+1.85" />.<br>
        Voor twee teams A en B wordt dan gewerkt met de standaardafwijking van de winstmarge:<br>
        <tex f="\sigma_W=\sqrt{\sigma_A^2+\sigma_B^2}"/>
        En dan nu hoe we aan <tx f="S_A" /> komen voor een geobserveerde winstmarge <tx f="w" />:<br>
        <tex f="S_A=\Phi\left(\frac{w}{\sigma_W}\right)"/>
        Hierbij is <tx f="\Phi"/> <a href="https://en.wikipedia.org/wiki/Normal_distribution#Cumulative_distribution_function" target="_blank">iets ingewikkelds</a>,
        <tx f="w"/> de winstmarge en <tx f="\mu_X"/> het scorend vermogen van Team X.<br>
        Je kunt het lezen als: <tx f="S_A" /> is de kans op een winstmarge tot en met <tx f="w"/>, geschaald op basis van het verwachte score-niveau.
        Let wel: bij verlies gebruiken we een negatieve winstmarge.
      </about-item>

      <about-item>
        <template #header>Oke, dus je houdt het scorend vermogen bij... #hoedan?</template>
        Dus nogmaals: het scorend vermogen <tx f="\mu_A"/> van Team A is hoeveel je verwacht dat dit team tegen een gelijksoortig team scoort.<br>
        Bij elke wedstrijd wordt een voorspelling van de thuisscore <tx f="X"/> en uitscore <tx f="Y"/> gedaan.
        Scoort een team meer dan deze voorspelling, dan gaat zijn scorend vermogen omhoog, en anders omlaag.
      </about-item>

      <about-item>
        <template #header>Is het scorend vermogen niet gewoon hetzelfde als je rating, maar dan anders?</template>
        Ja, en nee... Maar vooral nee... Maar misschien wel?<br>
        Misschien is de naam 'scorend vermogen' wat misleidend, want het geeft net zo goed aan hoeveel doelpunten een team verwacht door te laten<br>
        Neem bijvoorbeeld twee teams A en B met dezelfde rating, maar waarbij A een hoger scorend vermogen heeft dan B.<br>
        De teams zijn even sterk: een onderlinge wedstrijd zal dus gelijk op gaan.
        Ook zullen beide teams evenveel kans maken om te winnen tegen een ander team C.
        Maar daarbij zal de uitslag van A tegen C hoger zijn dan bij B tegen C<br>
        Bij korfbal is het scorend vermogen van sterkere teams over het algemeen hoger dan van teams van een lagere rating,
        maar bij voetbal is het bijvoorbeeld andersom.<br>
        Hoe komt dit? Zeg het maar...<br>
        Maar, om dus antwoord op de vraag te geven: nee, rating en scorend vermogen zijn niet vergelijkbaar, maar er is wel een relatie tussen de twee.<br>
        Inmiddels wordt die relatie voorzichtig gebruikt: als een team structureel meer of minder scoort dan je op basis van de rating zou verwachten,
        krijgt de rating een hele kleine extra correctie. Geen wilde magie, meer een statistisch duwtje in de rug.<br>
      </about-item>

      <about-item>
        <template #header>Effe een stapje terug: je zei dat bij elke wedstrijd een voorspelling wordt gedaan?</template>
        Klopt, dat werkt als volgt: bij elke voorspelling geldt dat <tx f="\mu_X+\mu_Y=X+Y"/>.
        Lees: het totaal aantal doelpunten in de voorspelling is gelijk aan de som van het scorend vermogen van beide teams.<br>
        Verder geldt dat de waarde van <tx f="S_A"/> die hoort bij de winstmarge <tx f="W=X-Y"/> gelijk moet zijn aan <tx f="E_A"/>.
        Oftewel: bij welke uitslag is het resultaat <tx f="S_A"/> gelijk aan het verwachte resultaat <tx f="E_A"/>,
        rekening houdend met het scorend vermogen van beide teams?<br>
        Let wel dat al deze getallen geen mooie ronde getallen zijn, maar decimale getallen. Bij de daadwerkelijke voorspelling wordt dit afgerond...
      </about-item>

      <about-item>
        <template #header>Met welke rating begint een team bij zijn eerste wedstrijd?</template>
        De start-ratings worden per team zo gekozen dat het voorspellend vermogen van het hele systeem optimaal is.<br>
        Ga ik niet verder uitleggen... Niet alle start-ratings zijn logisch, maar over het algemeen wel.<br>
        Wel start elk team met een hoge onzekerheid <tx f="RD"/>, wat ervoor zorgt dat de rating van nieuwe teams snel naar
        de 'correcte' waarde gaat.<br>
      </about-item>

      <about-item>
        <template #header>Zeg ik bedenk me nog: hoe zit het met thuisvoordeel???</template>
        Goeie! Thuisvoordeel is geen fabeltje en bestaat echt! Maar hoeveel thuisvoordeel heb je, en hoe druk je dat uit?<br>
        Heel simpel: bij alle berekeningen wordt de rating van het thuisteam verhoogd met een bepaalde universele waarde.
        Deze waarde is niet constant, maar wordt na elke wedstrijd aangepast.
        Presteert het thuisteam beter dan verwacht, dan gaat deze waarde omhoog, en anders gaat deze waarde omlaag. Dit principe houdt de waarde constant in balans.<br>
        Gedurende de geschiedenis van korfbal schommelt deze waarde tussen 28 en 39. Op dit moment is dit ongeveer 29.<br>
        Dit betekent concreet dat bij een wedstrijd tussen twee gelijkwaardige teams, het thuisteam 54% kans op winst heeft (als we geen gelijkspel toestaan).
      </about-item>

      <about-item>
        <template #header>Poh, wat een informatie... Maar even serieus, wat kun je hiermee?</template>
        Er is al benoemd dat de uitslagen van wedstrijden in de toekomst voorspeld kunnen worden, maar er is meer...<br>
        Wedstrijden in de toekomst kunnen niet alleen voorspeld worden, maar ook gesimuleerd worden.
        Een wedstrijd simuleren betekent dat er een willekeurige uitslag wordt gegenereerd, rekening houdend met de voorspelde uitslag.
        Deze kan natuurlijk afwijken van de voorspelling, maar hoe groter de afwijking, hoe kleiner de kans hierop<br>
        Doe je dit vervolgens met alle wedstrijden in de competitie, dan kun je kansen op kampioenschap/promotie/degradatie geven!<br>
        Dit wordt gedaan met <a href="https://nl.wikipedia.org/wiki/Monte-Carlosimulatie" target="_blank">Monte-Carlo</a>-simulatie.<br>
        In simpele woorden: de hele competitie wordt 100.000 keer gesimuleerd, rekening houdend met rating(-deviation) en scorend vermogen.<br>
        Per team wordt bijgehouden hoe vaak dit team aan het einde van een simulatie kampioen wordt, promoveert of degradeert.
        Komt het in die 100.000 keren 40.000 keer voor dat een team degradeert, dan is de kans op degradatie voor dat team 40%.
        <br>
        De simulatie is ook op de hoogte van al gespeelde wedstrijden in de competitie:<br>
        elke nacht, als jij nog ligt te pitten, halen kaboutertjes de nieuwste uitslagen uit Sportlink, waarbij
        ze vervolgens opnieuw 100.000 keer de competitie simuleren, gegeven de al gespeelde wedstrijden.<br>
      </about-item>
      </div>
    </template>

    <about-item>
      <template #header>Oeh, dus we kunnen zien wat onze kansen in de competitie zijn, hoe werkt dat?</template>
      Nou, scroll maar omhoog en klik een seizoen aan. Daar zie je alle poules van de standaardklassen. Kies je poule maar uit...
      <br>
      Hier zul je de tussenstand zien zoals je deze gewend bent, maar ook een kleurrijk balkje aan de rechterkant.
      Dit balkje geeft aan wat per team de 'competitiekansen' zijn: kans op kampioenschap (geel), promotie (groen) of degradatie (rood).<br>
      Hierin worden de PD-regelingen van het KNKV meegenomen, ook als het gaat om 'beste nummers <i>x</i> promoveren/degraderen'.<br>
      <br>
      Leuk om te weten: je kunt ook de competitiestand en -kansen inzien in het verleden door zelf de datum te verschuiven!<br>
      Twee dingen waar trouwens geen rekening wordt gehouden:
      strafpunten die nog in de toekomst gegeven kunnen worden,
      en het mogelijk terugtrekken/fuseren van teams <b>waarbij dit nog niet bekend is</b>.
      Indien dit al wel bekend is, dan zal dit vermeld worden bij het desbetreffende team, en wordt dit <i>wel</i> meegenomen in de getoonde competitiekansen.<br>
      <i>Op dit moment wordt rekening gehouden met de fusie van Noviomagum en Keizer Karel en de vorming van WKS uit SCO, SIOS / Leonidas en De Hoeve. Korfbal Elo gaat ervan uit dat dit effectief betekent dat Keizer Karel, SIOS / Leonidas en De Hoeve worden teruggetrokken.</i>
    </about-item>

    <about-item>
      <template #header>Kan Korfbal Elo mij ook zeggen wat toekomstige uitslagen zouden betekenen voor onze competitiekansen?</template>
      Wat denk je zelf? Korfbal Elo kan <span class="tooltip">alles<span class="tooltiptext">Behalve koken, je huis schoonmaken en jeuk op onbereikbare plekken weghalen 🥺</span></span>!
      Ga maar naar een teampagina, en druk op de knop 'Selecteer voor simulator'. Op alle pagina's zal het geselecteerde team een kek kleurtje krijgen.<br>
      Navigeer vervolgens naar een <span class="tooltip">relevante<span class="tooltiptext">een poule waar dit team in zit,
      of, indien dit voor PD relevant is, een poule in dezelfde klasse</span></span> pagina, en ge zult zien dat alle wedstrijden in het programma nu een knop 'Simuleer' krijgen.
      <br>
      Klik erop, en je apparaat verandert in een behoorlijk geavanceerde simulator...<br>
      Wat je hier kan doen: gegeven een winstmarge bereik (positief is winst voor thuisteam, negatief voor uitteam),
      gaat de simulator met een druk op de knop 'Start simulatie' de competitie continu simuleren, totdat je stop zegt...<br>
      Hoe langer je doorgaat, hoe nauwkeuriger de resultaten zijn. Maar let op, de simulator vreet net zoveel stroom als de zolderwietplantage van je buren, dus je batterij gaat snel leeg...
      <br>
      Het resultaat is een hele lading balkjes, zoals je die kent bij de tussenstanden van een poule, met voor elk balkje een getal.
      Dat getal geeft de winstmarge aan voor het thuisteam (negatief voor uitwinst). Het balkje naast elk getal geeft de competitiekansen aan,
      gegeven dat die wedstrijd een uitslag heeft met die winstmarge.<br>
      Je zult zien dat meestal de sprongen van -1 (uitwinst) naar 0 (gelijkspel) en naar 1 (thuiswinst) het grootst zijn.<br>
      Maar soms is er ook een groot verschil rondom andere marges, dit kan dan weer door onderling resultaat komen indien het om een returnwedstrijd gaat.
    </about-item>

    <about-item>
      <template #header>En die pagina met onderlinge wedstrijden dan?</template>
      Die bestaat ook gewoon. In uitslagen/tabellen kun je op een gespeelde wedstrijd klikken, of vanuit teamstatistieken doorklikken,
      en dan krijg je een pagina met de complete bekende geschiedenis tussen twee teams.<br>
      Inclusief aantallen zeges/gelijkspellen en alle gevonden onderlinge uitslagen.
    </about-item>

    <about-item>
      <template #header>Trouwens he, hoe kom je aan ±300.000 wedstrijduitslagen?</template>
      Archieven van het <a href="https://www.delpher.nl/thema/sport/korfbal" target="_blank">Nederlands Korfbalblad</a>,
      verschillende <a href="https://www.delpher.nl/nl/kranten" target="_blank">kranten</a>,
      de Uitslagendienst van KV Antilopen (wat mis ik die site...) en vanaf 2022 Sportlink.<br>
    </about-item>

    <about-item>
      <template #header>Poh, kan ik die hele lading wedstrijden ook downloaden?</template>
      Ja dat kan zeker! Zowel de hele set, of de wedstrijden per vereniging.<br>
      Voor de gehele zooi: ga naar <router-link to="/data">Data</router-link>. De eerste keer kan even duren; daarna bewaart je browser de wedstrijddata lokaal zolang de dataset niet verandert.<br>
      Voor de wedstrijden per vereniging: ga naar een willekeurig team, druk op de knop <i>Statistieken</i> en druk op
      <i>Download wedstrijden als CSV</i>
    </about-item>

    <about-item>
      <template #header>Waarom alleen eerste teams, waarom geen reserveteams en jeugdteams?</template>
      Tja, dat is gewoon een keuze geweest. Helaas bellenblaas...<br>
      Maar bedenk ook: het eerste seniorenteam is meestal het beste team van een vereniging.
      De samenstelling is niet zo veranderlijk als bij reserveteams, en spelers schuiven niet constant door zoals bij jeugdteams.
      Omdat hier geen informatie over spelers gebruikt wordt, zou deze methode simpelweg veel minder goed werken voor reserve en/of jeugdteams.
    </about-item>

    <about-item>
      <template #header>Ik wist het, toen ik 60 jaar geleden in het eerste speelde waren we veel beter dan die kneusjes van nu</template>
      Aldus Jan, 90 jaar, die ziet dat de rating van zijn vereniging 60 jaar geleden hoger is dan nu.<br>
      Nou waarschijnlijk niet Jan... In de regel hebben alle ratings op elk moment een gemiddelde van 1500.<br>
      Maar het gemiddelde team van 60 jaar geleden hoeft niet zo sterk te zijn als het gemiddelde team van nu.
      Sterker nog, waarschijnlijk is het gemiddelde team van 60 jaar geleden vele malen zwakker dan een gemiddeld Nederland.
      Kortom, je kunt ratings uit verschillende jaren moeilijk vergelijken; een rating geeft alleen aan hoe sterk een team is
      in vergelijking met gemiddeld Nederland.
    </about-item>

    <about-item>
      <template #header>Als ik op de pagina van sommige teams zit zie ik een grafiek met meerdere teams. Wasda???</template>
      Dit zijn voorgangers van de betreffende (fusie)vereniging! Fusies worden als volgt meegenomen: de rating van de fusie vereniging
      is gelijk aan de hoogste rating van de voorgangers.<br>
      Ook komt het vaak voor dat nadat een vereniging werd opgeheven, de leden aansloten bij een bestaande vereniging
      In dit geval wordt dit ook als een 'fusie' gezien.<br>
      Let wel: informatie hierover is moeilijk te vinden en niet altijd correct...
    </about-item>

    <about-item>
      <template #header>Als ik op de pagina van sommige teams kijk lijken ze lange periodes zonder wedstrijden te hebben gehad. Hoe zit dat???</template>
      Dit kan verschillende oorzaken hebben. Lang niet van alle competities zijn bijvoorbeeld wedstrijduitslagen terug te vinden online.
      Over het algemeen geldt: de landelijke competitie is redelijk compleet tot en met 1955,
      want het <a href="https://www.delpher.nl/thema/sport/korfbal" target="_blank">Nederlands Korfbalblad</a> is tot 1955 gedigitaliseerd.<br>
      Na 1955 komen de uitslagen vooral uit kranten, echter zijn ook deze niet allemaal gedigitaliseerd.<br>
      Ook uitslagen van sommige regionale bonden zijn veel lastiger te vinden.<br>
      Vanaf het jaar ±2000 zijn wel alle uitslagen beschikbaar. Periodes van inactiviteit na dit jaar kan komen doordat een team enkel actief was
      in de breedtekorfbal en niet tegen andere eerste teams actief was.<br>
      En als laatste reden: indien de betrouwbaarheid van de rating te laag is (door een te lange periode van inactiviteit),
      dan wordt deze niet weergegeven in de grafieken.
    </about-item>

    <about-item>
      <template #header>Sommige teams lijken pas actief vanaf jaar X, maar ze bestonden allang voor dat jaar</template>
      Dit kan komen door dezelfde reden als hierboven. Een andere reden kan zijn dat het om christelijke teams gaat.
      De competitie van de christelijke korfbalbond is (nog) niet meegenomen.
      In 1970 zijn de neutrale en christelijke korfbalbond samengegaan en zijn ook de christelijke teams meegenomen.
    </about-item>

    <about-item>
      <template #header>Mijn mobiel/prehistorische laptop loopt vast bij sommige pagina's</template>
      Er is vrij weinig moeite gedaan om de grafiekjes te optimaliseren, moet nog gebeuren...
    </about-item>

    <about-item>
      <template #header>Ik heb nog oude exemplaren van het Nederlands Korfbalblad, heb je daar wat aan?</template>
      Gaaf! Heb je exemplaren van tussen 1955 en 2000? Zo ja, digitaliseer ze (of maak een foto van de uitslagen) en <a href="mailto:korfbalelo@gmail.com">stuur ze dan op</a>.<br>
      Heb je andere archieven met oude korfbaluitslagen die nog niet in de dataset lijken te staan? Ook dan kun je ze opsturen.
    </about-item>

    <about-item>
      <template #header>Ik ben wel benieuwd, hoe ziet de 'achterkant' van Korfbal Elo er precies uit?</template>
      Vraag je nou of ik <a href="https://www.youtube.com/watch?v=sWGsiMsH4ek&t=37s" target="_blank">m'n achterkantje laat zien</a>?...<br>
      Ooh, je bedoelt de broncode...<br>
      Tja<br>
      Hoe zal ik dit zeggen...<br>
      Beeld je de hemel der software in, waarin alle regels code logisch zijn. Waarbij elk stukje complexiteit keurig netjes is gedocumenteerd.
      Vol met trotse software ontwikkelaars die hun creaties vertroetelen alsof het hun meest geliefde kinderen zijn.
      Compleet vrij van bugs. Het is de plek waar alleen software leeft waar zelfs God trots op zou zijn.<br>
      Kun je het je inbeelden?<br>
      Nou, dan zit ik, samen met de broncode van Korfbal Elo (en die van de Belastingdienst), te creperen in de diepste krochten van de software-hel.<br>
      Met andere woorden: die broncode is nou niet iets wat ik trots aan de buitenwereld laat zien...<br>
      Maar voor wie dan toch heel graag wil zien hoe dit ding in elkaar is gezet, zoek het GitHub knopje...
    </about-item>

    <about-item>
      <template #header>Ik wil screenshots/data van deze site gebruiken voor wat dan ook. Mag dat?</template>
      Ga gerust je gang, het liefst wel met verwijzing naar deze site. <sup class="small">(anders kom ik je achterna)</sup>
    </about-item>

    <about-item>
      <template #header>Ik heb vragen, opmerkingen, frustraties, liefdesverklaringen  etc. aan de hand van deze onzin</template>
      <a href="mailto:korfbalelo@gmail.com">Kom dan</a>
    </about-item>

    <about-item>
      <template #header>Welke gek heeft dit trouwens gemaakt?</template>
      De naam is Onis, Ramon Onis 😎
    </about-item>
  </div>
</template>
<script lang="ts" setup>
import AboutItem from '@/components/AboutItem.vue'
import Tex from '@/components/Tex.vue'
import Tx from '@/components/Tx.vue'
import { ref } from 'vue'

const showMath = ref(false)
const hasShownMath = ref(false)
</script>

<style>
.about-view {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 80%;
  margin: 0 auto;
  height: 100%;
}
   /* Tooltip container */
 .tooltip {
   position: relative;
   display: inline-block;
   border-bottom: 1px dotted black !important; /* Add dots under the hoverable text */
   cursor: pointer;
 }

/* Tooltip text */
.tooltiptext {
  visibility: hidden; /* Hidden by default */
  width: 130px;
  background-color: black;
  color: #ffffff;
  text-align: center;
  padding: 5px 0;
  border-radius: 6px;
  position: absolute;
  z-index: 1; /* Ensure tooltip is displayed above content */
}

/* Show the tooltip text on hover */
.tooltip:hover .tooltiptext {
  visibility: visible;
}
</style>
