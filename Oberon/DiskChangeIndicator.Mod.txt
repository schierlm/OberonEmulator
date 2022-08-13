MODULE DiskChangeIndicator;
  IMPORT SYSTEM, Kernel;

  VAR mmio: INTEGER;

  PROCEDURE Write(val: INTEGER);
  BEGIN IF mmio # 0 THEN SYSTEM.PUT(mmio, val) END
  END Write;

  PROCEDURE MarkUnchg*;
  BEGIN Write(0)
  END MarkUnchg;

  PROCEDURE MarkChg*;
  BEGIN Write(1)
  END MarkChg;

  PROCEDURE DisableUpd*;
  BEGIN Write(2)
  END DisableUpd;

  PROCEDURE EnableUpd*;
  BEGIN Write(3)
  END EnableUpd;

BEGIN
  SYSTEM.PUT(Kernel.hwEnumerator, Kernel.ToHardwareID("DChg"));
  SYSTEM.GET(Kernel.hwEnumerator, mmio);
END DiskChangeIndicator.
