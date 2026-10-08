# Topic migration in v0.17

Version 0.17 replaces the six provisional topics with eight. They remain **provisional**: they follow the themes Migri and the working group have named for the 2027 citizenship test, but they are this app's grouping, not an official syllabus or exam structure.

| Order | Topic ID | Finnish | Swedish | English |
|---|---|---|---|---|
| 1 | `democracy` | Demokratia ja päätöksenteko | Demokrati och beslutsfattande | Democracy and decision-making |
| 2 | `rights` | Perus- ja ihmisoikeudet | Grundläggande och mänskliga rättigheter | Fundamental and human rights |
| 3 | `equality` | Yhdenvertaisuus ja tasa-arvo | Likabehandling och jämställdhet | Equality and non-discrimination |
| 4 | `laws` | Lait arjessa | Lagar i vardagen | Laws in everyday life |
| 5 | `history` | Suomen historia | Finlands historia | Finnish history |
| 6 | `culture` | Kulttuuri, kielet ja yhteiskunta | Kultur, språk och samhälle | Culture, languages and society |
| 7 | `safety` | Turvallisuus ja hätätilanteet | Säkerhet och nödsituationer | Safety and emergencies |
| 8 | `digital` | Digitaalinen asiointi | Digitala tjänster | Digital services |

The IDs `democracy`, `rights`, `history` and `culture` are kept, so saved navigation and history stay readable. `work` and `everyday` are retired; their questions moved as below.

## How progress survives

Moving a question to another topic, or changing its learning objectives, is **reclassification**. It does not change what the learner practises, so from v0.17 it needs no question revision. Question IDs and revisions stay the same, current mastery and review dates are kept, and every attempt remains in the history. The import preview counts these questions as "reclassified". Saved attempts keep the topic they were answered under, while statistics group them by the question's current topic. A backup restore no longer requires an attempt's saved topic to match the installed question.

Changing the practised content still requires a new revision as before. The later content upgrade in v0.17 (four options, editorial status) revises the existing concepts, which resets their *current* mastery while keeping all history.

## Question mapping

Swedish variants (`<id>-sv`) follow their Finnish counterparts.

| Question | Old topic | New topic | Objectives |
|---|---|---|---|
| `demo-parliament` | democracy | democracy | `dem-powers` |
| `demo-members` | democracy | democracy | `dem-parliament` |
| `demo-term` | democracy | democracy | `dem-parliament` |
| `demo-voting` | democracy | democracy | `dem-elections` |
| `demo-courts` | democracy | democracy | `dem-powers` |
| `demo-religion` | rights | rights | `rights-religion` |
| `demo-privacy` | rights | rights | `rights-privacy` |
| `demo-basic-education` | rights | rights | `rights-education` |
| `demo-expression` | culture | rights | `rights-expression` |
| `demo-assembly` | culture | rights | `rights-assembly` |
| `demo-association` | culture | rights | `rights-assembly` |
| `demo-equality` | rights | equality | `eq-law` |
| `demo-follow-law` | rights | laws | `law-everyone` |
| `demo-contract` | work | laws | `law-employment` |
| `demo-oral-contract` | work | laws | `law-employment` |
| `demo-orientation` | work | laws | `law-employment`, `law-work-safety` |
| `demo-safety` | work | laws | `law-work-safety` |
| `demo-protective-equipment` | work | laws | `law-work-safety` |
| `demo-independence` | history | history | `hist-independence` |
| `demo-eu` | history | history | `hist-integration` |
| `demo-sweden` | history | history | `hist-sweden` |
| `demo-autonomy` | history | history | `hist-autonomy` |
| `demo-suffrage` | history | history | `hist-autonomy`, `eq-gender` |
| `demo-languages` | culture | culture | `cult-languages` |
| `demo-sami` | culture | culture | `cult-minorities` |
| `demo-library` | everyday | culture | `cult-services` |
| `demo-library-fee` | everyday | culture | `cult-services` |
| `demo-library-return` | everyday | culture | `cult-services` |
| `demo-emergency` | everyday | safety | `safe-112` |
| `demo-emergency-call` | everyday | safety | `safe-112` |

The authored lessons of the retired `work` and `everyday` topics leave the package with those topics. Lessons for the new topics are added with the content expansion.
