import React from 'react'
import {
  AimOutlined,
  ApartmentOutlined,
  AppstoreOutlined,
  ArrowLeftOutlined,
  BuildOutlined,
  CheckOutlined,
  CloudUploadOutlined,
  ColumnWidthOutlined,
  DashboardOutlined,
  DeleteOutlined,
  EditOutlined,
  EnvironmentOutlined,
  ExpandOutlined,
  FileTextOutlined,
  FolderOpenOutlined,
  GlobalOutlined,
  HomeOutlined,
  KeyOutlined,
  LaptopOutlined,
  LogoutOutlined,
  PercentageOutlined,
  PlusOutlined,
  RobotOutlined,
  SafetyCertificateOutlined,
  SaveOutlined,
  SearchOutlined,
  SendOutlined,
  TeamOutlined,
  UserAddOutlined,
  UserOutlined,
  UserSwitchOutlined,
} from '@ant-design/icons'

type LegacyIconProps = {
  fontSize?: 'small' | 'medium' | 'large' | string
  color?: string
  sx?: React.CSSProperties & Record<string, any>
  style?: React.CSSProperties
  className?: string
  spin?: boolean
  rotate?: number
  twoToneColor?: string
  [key: string]: any
}

function wrap(Icon: React.ComponentType<any>) {
  return function LegacyAntIcon({
    fontSize,
    color,
    sx,
    style,
    ...props
  }: LegacyIconProps) {
    const size =
      fontSize === 'small' ? 16 :
      fontSize === 'large' ? 28 :
      fontSize === 'medium' ? 20 :
      undefined

    const mappedColor =
      color === 'primary' ? '#2563EB' :
      color === 'action' ? '#667085' :
      color === 'error' ? '#DC2626' :
      color

    return (
      <Icon
        {...props}
        style={{
          fontSize: size,
          color: mappedColor,
          ...(sx || {}),
          ...(style || {}),
        }}
      />
    )
  }
}

export const AddRoundedIcon = wrap(PlusOutlined)
export const AdminPanelSettingsRoundedIcon = wrap(SafetyCertificateOutlined)
export const ApartmentRoundedIcon = wrap(ApartmentOutlined)
export const ArrowBackRoundedIcon = wrap(ArrowLeftOutlined)
export const ArticleRoundedIcon = wrap(FileTextOutlined)
export const CheckRoundedIcon = wrap(CheckOutlined)
export const CloudUploadRoundedIcon = wrap(CloudUploadOutlined)
export const ConstructionRoundedIcon = wrap(BuildOutlined)
export const DeleteOutlineRoundedIcon = wrap(DeleteOutlined)
export const DescriptionRoundedIcon = wrap(FileTextOutlined)
export const DevicesRoundedIcon = wrap(LaptopOutlined)
export const EditRoundedIcon = wrap(EditOutlined)
export const FolderRoundedIcon = wrap(FolderOpenOutlined)
export const GroupsRoundedIcon = wrap(TeamOutlined)
export const HomeWorkRoundedIcon = wrap(HomeOutlined)
export const HowToRegRoundedIcon = wrap(UserAddOutlined)
export const LocationOnRoundedIcon = wrap(EnvironmentOutlined)
export const LockResetRoundedIcon = wrap(KeyOutlined)
export const LogoutRoundedIcon = wrap(LogoutOutlined)
export const ManageAccountsRoundedIcon = wrap(UserSwitchOutlined)
export const MapRoundedIcon = wrap(GlobalOutlined)
export const MyLocationRoundedIcon = wrap(AimOutlined)
export const PercentRoundedIcon = wrap(PercentageOutlined)
export const PersonRoundedIcon = wrap(UserOutlined)
export const PsychologyRoundedIcon = wrap(RobotOutlined)
export const SaveRoundedIcon = wrap(SaveOutlined)
export const SearchRoundedIcon = wrap(SearchOutlined)
export const SendRoundedIcon = wrap(SendOutlined)
export const SpaceDashboardRoundedIcon = wrap(DashboardOutlined)
export const SquareFootRoundedIcon = wrap(ExpandOutlined)
export const StraightenRoundedIcon = wrap(ColumnWidthOutlined)
export const FallbackRoundedIcon = wrap(AppstoreOutlined)