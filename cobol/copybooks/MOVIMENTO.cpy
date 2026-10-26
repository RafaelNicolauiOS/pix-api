*> ================================================================
*> MOVIMENTO - arquivo exportado pela pix-api (GET /batch/movimento)
*> Registro de 150 bytes. Contrato com LayoutMovimento.java.
*>
*> Notacao: 9(n) = n digitos | X(n) = n caracteres
*>          V    = virgula decimal implicita (nao gravada no arquivo)
*>          88   = "nivel de condicao": um nome para um valor do campo
*>                 (como um case de enum testado com IF MOV-HEADER)
*> ================================================================
 01  MOV-REGISTRO.
     05  MOV-TIPO                  PIC X(01).
         88  MOV-HEADER                      VALUE 'H'.
         88  MOV-CONTA                       VALUE 'C'.
         88  MOV-LANCAMENTO                  VALUE 'L'.
         88  MOV-TRAILER                     VALUE 'T'.
     05  MOV-DADOS                 PIC X(149).
*>   REDEFINES: a mesma area de memoria lida com outro layout, conforme o tipo.
     05  MOV-H REDEFINES MOV-DADOS.
         10  MOV-H-DATA-REF        PIC 9(08).
         10  MOV-H-GERADO-EM       PIC 9(14).
         10  MOV-H-SISTEMA         PIC X(10).
         10  FILLER                PIC X(117).
     05  MOV-C REDEFINES MOV-DADOS.
         10  MOV-C-CONTA-ID        PIC 9(10).
         10  MOV-C-CHAVE           PIC X(77).
         10  MOV-C-TITULAR         PIC X(30).
         10  MOV-C-SALDO           PIC 9(13)V99.
         10  FILLER                PIC X(17).
     05  MOV-L REDEFINES MOV-DADOS.
         10  MOV-L-CONTA-ID        PIC 9(10).
         10  MOV-L-DATA            PIC 9(08).
         10  MOV-L-HORA            PIC 9(06).
         10  MOV-L-NATUREZA        PIC X(01).
             88  MOV-L-CREDITO               VALUE 'C'.
             88  MOV-L-DEBITO                VALUE 'D'.
         10  MOV-L-TIPO-LANC       PIC X(12).
         10  MOV-L-VALOR           PIC 9(13)V99.
         10  MOV-L-TRANSF-ID       PIC X(36).
         10  FILLER                PIC X(61).
     05  MOV-T REDEFINES MOV-DADOS.
         10  MOV-T-QTD-CONTAS      PIC 9(09).
         10  MOV-T-QTD-LANC        PIC 9(09).
         10  MOV-T-TOT-CREDITOS    PIC 9(15)V99.
         10  MOV-T-TOT-DEBITOS     PIC 9(15)V99.
         10  FILLER                PIC X(97).
