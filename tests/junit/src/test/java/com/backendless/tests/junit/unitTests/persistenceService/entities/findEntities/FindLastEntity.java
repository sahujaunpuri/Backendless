package com.backendless.tests.junit.unitTests.persistenceService.entities.findEntities;

public class FindLastEntity extends BaseFindEntity
{
  @Override
  public boolean equals( Object o )
  {
    if( !o.getClass().equals( this.getClass() ) )
      return false;

    return super.equals( o );
  }
}
