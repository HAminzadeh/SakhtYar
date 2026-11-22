export const messages = {
  fa: {
    appName:'ساخت‌یار',appSubtitle:'سامانه مدیریت هوشمند مشارکت در ساخت',loginTitle:'خوش آمدید',
    loginHint:'برای ورود، اطلاعات حساب خود را وارد کنید.',username:'نام کاربری',password:'رمز عبور',
    login:'ورود به ساخت‌یار',register:'ایجاد حساب جدید',invalidLogin:'نام کاربری یا رمز عبور صحیح نیست.',
    language:'زبان',settings:'تنظیمات',country:'کشور',currency:'ارز پیش‌فرض',timezone:'منطقه زمانی',
    theme:'پوسته',themeHint:'پوسته دلخواه خود را انتخاب کنید؛ تغییرات بلافاصله پیش‌نمایش می‌شوند.',
    save:'ذخیره',saved:'تنظیمات ذخیره شد.',cases:'پرونده‌ها',users:'کاربران',account:'حساب من',logout:'خروج',
    selectCountry:'انتخاب کشور',selectDivision:'استان / ایالت / ناحیه',selectCity:'انتخاب شهر',
    masterData:'داده‌های مرجع جهانی',masterDataHint:'کشورها، تقسیمات کشوری و شهرهای GeoNames را همگام‌سازی کنید.',
    startImport:'شروع همگام‌سازی GeoNames',importRunning:'همگام‌سازی در حال اجراست',
    countries:'کشورها',divisions:'تقسیمات کشوری',cities:'شهرها',currencies:'ارزها',
  },
  en: {
    appName:'SakhtYar',appSubtitle:'Smart Construction Partnership Management',loginTitle:'Welcome',
    loginHint:'Enter your account details to sign in.',username:'Username',password:'Password',
    login:'Sign in to SakhtYar',register:'Create a new account',invalidLogin:'Username or password is incorrect.',
    language:'Language',settings:'Settings',country:'Country',currency:'Default currency',timezone:'Time zone',
    theme:'Theme',themeHint:'Choose your preferred visual theme. Changes are previewed immediately.',
    save:'Save',saved:'Settings saved.',cases:'Cases',users:'Users',account:'My account',logout:'Sign out',
    selectCountry:'Select country',selectDivision:'Province / State / Region',selectCity:'Select city',
    masterData:'Global Master Data',masterDataHint:'Synchronize countries, administrative divisions, and cities from GeoNames.',
    startImport:'Start GeoNames synchronization',importRunning:'Synchronization is running',
    countries:'Countries',divisions:'Administrative divisions',cities:'Cities',currencies:'Currencies',
  },
} as const
export type LanguageCode=keyof typeof messages
export type MessageKey=keyof typeof messages.fa