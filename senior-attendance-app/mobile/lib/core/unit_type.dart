enum UnitType {
  publicInterest('PUBLIC_INTEREST', '공익형', '지역사회를 위한 봉사 성격의 일자리입니다.'),
  market('MARKET', '시장형', '수익을 창출하는 전문적인 일자리입니다.'),
  socialService('SOCIAL_SERVICE', '사회서비스형', '사회적으로 필요한 서비스를 제공하는 일자리입니다.');

  final String apiValue;
  final String label;
  final String description;

  const UnitType(this.apiValue, this.label, this.description);
}
